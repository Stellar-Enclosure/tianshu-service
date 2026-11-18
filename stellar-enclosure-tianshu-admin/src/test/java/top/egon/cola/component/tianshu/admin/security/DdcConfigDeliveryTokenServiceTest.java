package top.egon.cola.component.tianshu.admin.security;

import com.fasterxml.jackson.databind.node.BooleanNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigSnapshotDTO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcConfigWorkloadIdentityVO;
import top.egon.cola.component.tianshu.admin.security.config.DdcConfigDeliveryTokenService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DdcConfigDeliveryTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T16:00:00Z");
    private static final String TYPE = "top.egon.cola.component.tianshu.admin.security.config.DdcConfigDeliveryTokenService";
    private static final String IDENTITY = "top.egon.cola.component.tianshu.admin.model.vo.DdcConfigWorkloadIdentityVO";
    private static final String KEY_A = "01234567890123456789012345678901";
    private static final String KEY_B = "98765432109876543210987654321098";

    @TempDir
    Path directory;

    @Test
    void signedDeliveryProofCarriesConfirmedFenceAndConfigurationThroughActualIssueAndVerify() throws Exception {
        Path keys = directory.resolve("delivery-keys.json");
        Files.writeString(keys, keyring());
        Class<?> type = Class.forName(TYPE);
        Class<?> identity = Class.forName(IDENTITY);
        Object service = type.getConstructor(Path.class, Clock.class, Duration.class)
                .newInstance(keys, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(60));
        var scope = new DdcConfigScopeVO("1001", "reading", "prod", "reader", "application.yml");
        Object workload = identity.getConstructors()[0].newInstance(new DdcTenantContextDTO(1001L, "7001", "service:reader"),
                scope, "reader-prod", 3L, "cluster-prod", "reader", "workload-a", "pod-a", "application",
                "containerd://one", "boot-a");
        var draft = new DdcConfigSnapshotDTO("1", scope, "43", true, "sha256:" + "a".repeat(64),
                "sha256:" + "0".repeat(64), Map.of("enabled", BooleanNode.TRUE), null, NOW.plusSeconds(120));
        var snapshot = new DdcConfigSnapshotDTO("1", scope, "43", true, draft.getRestartDigest(),
                DdcConfigCanonicalJson.contentSha256(draft), draft.getHotValues(), null, draft.getValidUntil());

        String token = (String) type.getMethod("issue", identity, long.class, long.class, DdcConfigSnapshotDTO.class)
                .invoke(service, workload, 901L, 820001L, snapshot);
        Object claims = type.getMethod("verify", String.class, identity).invoke(service, token, workload);

        assertThat(token).startsWith("dct1.key-a.").doesNotContain("reader-secret");
        assertThat(claims.getClass().getMethod("bootFence").invoke(claims)).isEqualTo(901L);
        assertThat(claims.getClass().getMethod("configId").invoke(claims)).isEqualTo(820001L);
        assertThat(claims.getClass().getMethod("releaseRevision").invoke(claims)).isEqualTo("43");
        assertThat(claims.getClass().getMethod("targetBindingRevision").invoke(claims)).isEqualTo(3L);
        assertThat(claims.getClass().getMethod("expiresAt").invoke(claims)).isEqualTo(NOW.plusSeconds(60));
    }

    private static String keyring() {
        return keyring(1, "key-a", key("key-a", KEY_A, NOW.plusSeconds(3600)));
    }

    @Test
    void clampsExpiryToConfirmedSnapshotAndActiveKeyAndRejectsTheExactExpirationInstant() throws Exception {
        Path keys = file(keyring(1, "key-a", key("key-a", KEY_A, NOW.plusSeconds(30))));
        var issuer = service(keys, NOW);
        var workload = workload("boot-a", "service:reader", 3);
        String snapshotBound = issuer.issue(workload, 901, 820001, snapshot(workload.scope(), NOW.plusSeconds(20)));
        assertThat(issuer.verify(snapshotBound, workload).expiresAt()).isEqualTo(NOW.plusSeconds(20));
        String keyBound = issuer.issue(workload, 901, 820001, snapshot(workload.scope(), NOW.plusSeconds(120)));
        assertThat(issuer.verify(keyBound, workload).expiresAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(service(keys, NOW.plusSeconds(19)).verify(snapshotBound, workload).configId()).isEqualTo(820001L);
        rejected(() -> service(keys, NOW.plusSeconds(20)).verify(snapshotBound, workload));
    }

    @Test
    void rotatesActiveKeyWithAnExplicitOlderVerificationWindowAndNoRemovedKeyFallback() throws Exception {
        Path keys = file(keyring());
        var issuer = service(keys, NOW);
        var workload = workload("boot-a", "service:reader", 3);
        String previous = issuer.issue(workload, 901, 820001, snapshot(workload.scope(), NOW.plusSeconds(120)));
        Files.writeString(keys, keyring(2, "key-b", key("key-a", KEY_A, NOW.plusSeconds(30)), key("key-b", KEY_B, NOW.plusSeconds(3600))));
        String current = issuer.issue(workload, 901, 820001, snapshot(workload.scope(), NOW.plusSeconds(120)));
        assertThat(current).startsWith("dct1.key-b.");
        assertThat(issuer.verify(previous, workload).bootFence()).isEqualTo(901L);
        rejected(() -> service(keys, NOW.plusSeconds(30)).verify(previous, workload));
        assertThat(service(keys, NOW.plusSeconds(30)).verify(current, workload).bootFence()).isEqualTo(901L);
        Files.writeString(keys, keyring(3, "key-b", key("key-b", KEY_B, NOW.plusSeconds(3600))));
        rejected(() -> issuer.verify(previous, workload));
        assertThat(issuer.verify(current, workload).bootFence()).isEqualTo(901L);
    }

    @Test
    void rechecksEveryStableTenantTargetAndIncarnationFieldButAllowsAuditActorRotation() throws Exception {
        Path keys = file(keyring());
        var service = service(keys, NOW);
        var original = workload("boot-a", "service:reader", 3);
        String token = service.issue(original, 901, 820001, snapshot(original.scope(), NOW.plusSeconds(120)));
        for (String field : List.of("identityTenantId", "targetBindingId", "targetBindingRevision", "clusterId", "k8sNamespace",
                "workloadUid", "podUid", "containerName", "containerId", "bootId", "scope")) {
            rejected(() -> service.verify(token, changed(original, field)));
        }
        assertThat(service.verify(token, workload("boot-a", "service:rotated-credential", 3)).bootId()).isEqualTo("boot-a");
        var otherScope = new DdcConfigScopeVO("1002", "reading", "prod", "reader", "application.yml");
        var otherTenant = new DdcConfigWorkloadIdentityVO(new DdcTenantContextDTO(1002L, "7002", "service:reader"), otherScope,
                "reader-prod", 3, "cluster-prod", "reader", "workload-a", "pod-a", "application", "containerd://one", "boot-a");
        rejected(() -> service.verify(token, otherTenant));
    }

    @Test
    void rejectsMalformedAndEvenCorrectlySignedNoncanonicalOrUnknownClaims() throws Exception {
        Path keys = file(keyring());
        var service = service(keys, NOW);
        var workload = workload("boot-a", "service:reader", 3);
        String token = service.issue(workload, 901, 820001, snapshot(workload.scope(), NOW.plusSeconds(120)));
        for (String malformed : List.of("", "dct2" + token.substring(4), token + ".extra", token + "=", "x".repeat(8193),
                token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A"))) {
            rejected(() -> service.verify(malformed, workload));
        }
        ObjectNode source = (ObjectNode) DdcConfigCanonicalJson.parse(Base64.getUrlDecoder().decode(token.split("\\.")[2]));
        for (String field : List.of("unknown", "numericFence", "revision", "enabled", "schema", "time", "scope")) {
            ObjectNode altered = source.deepCopy();
            switch (field) {
                case "unknown" -> altered.put("alg", "none");
                case "numericFence" -> altered.put("bootFence", 901);
                case "revision" -> altered.put("releaseRevision", "043");
                case "enabled" -> altered.put("enabled", "true");
                case "schema" -> altered.put("schemaVersion", "2");
                case "time" -> altered.put("expiresAt", "2026-11-18T16:01:00+00:00");
                case "scope" -> ((ObjectNode) altered.get("scope")).put("unknown", "ignored");
                default -> throw new AssertionError();
            }
            String forged = signed(DdcConfigCanonicalJson.canonicalBytes(altered));
            rejected(() -> service.verify(forged, workload));
        }
        rejected(() -> service.verify(signed((new String(DdcConfigCanonicalJson.canonicalBytes(source), java.nio.charset.StandardCharsets.UTF_8) + " ")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)), workload));
    }

    @Test
    void sameVersionContradictionRollbackMalformedOrMissingProjectionIsUnavailableWithoutLkgOrSecretCauses() throws Exception {
        Path keys = file(keyring());
        var service = service(keys, NOW);
        var workload = workload("boot-a", "service:reader", 3);
        String token = service.issue(workload, 901, 820001, snapshot(workload.scope(), NOW.plusSeconds(120)));
        Files.writeString(keys, keyring(1, "key-b", key("key-b", KEY_B, NOW.plusSeconds(3600))));
        unavailable(() -> service.verify(token, workload));
        String accepted = keyring(2, "key-b", key("key-b", KEY_B, NOW.plusSeconds(3600)));
        Files.writeString(keys, accepted);
        String current = service.issue(workload, 901, 820001, snapshot(workload.scope(), NOW.plusSeconds(120)));
        Files.writeString(keys, keyring());
        unavailable(() -> service.verify(current, workload));
        for (String invalid : List.of(accepted.replace("\"schemaVersion\":\"1\"", "\"schemaVersion\":1"),
                accepted.replace("\"keys\":", "\"unknown\":true,\"keys\":"),
                keyring(3, "key-b", key("key-b", "short", NOW.plusSeconds(3600))),
                keyring(3, "key-b", key("key-b", KEY_B, NOW.plusSeconds(3600)), key("key-b", KEY_B, NOW.plusSeconds(3600))),
                accepted + "{}", " ".repeat(65_537))) {
            Files.writeString(keys, invalid);
            unavailable(() -> service.verify(current, workload));
        }
        Files.write(keys, new byte[] { '{', (byte) 0xc3, (byte) 0x28, '}' });
        unavailable(() -> service.verify(current, workload));
        Files.delete(keys);
        unavailable(() -> service.verify(current, workload));
        Files.writeString(keys, accepted);
        assertThat(service.verify(current, workload).configId()).isEqualTo(820001L);
    }

    @Test
    void concurrentCallsUseIndependentMacInstancesAndDeterministicConfirmedClaims() throws Exception {
        Path keys = file(keyring());
        var service = service(keys, NOW);
        var workload = workload("boot-a", "service:reader", 3);
        var snapshot = snapshot(workload.scope(), NOW.plusSeconds(120));
        try (var workers = Executors.newFixedThreadPool(8)) {
            List<java.util.concurrent.Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                futures.add(workers.submit(() -> {
                    String token = service.issue(workload, 901, 820001, snapshot);
                    assertThat(service.verify(token, workload).bootFence()).isEqualTo(901L);
                    return token;
                }));
            }
            List<String> tokens = new ArrayList<>();
            for (var future : futures) { tokens.add(future.get(5, TimeUnit.SECONDS)); }
            assertThat(tokens.stream().distinct().count()).isEqualTo(1);
        }
    }

    @Test
    void readsOneKubeletProjectionGenerationButRejectsASymlinkOutsideItsVolume() throws Exception {
        Path volume = Files.createDirectory(directory.resolve("volume"));
        Path generation = Files.createDirectory(volume.resolve("..generation"));
        Files.writeString(generation.resolve("keys.json"), keyring());
        Files.createSymbolicLink(volume.resolve("..data"), Path.of("..generation"));
        Path keys = Files.createSymbolicLink(volume.resolve("keys.json"), Path.of("..data/keys.json"));
        var service = service(keys, NOW);
        var workload = workload("boot-a", "service:reader", 3);
        String token = service.issue(workload, 901, 820001, snapshot(workload.scope(), NOW.plusSeconds(120)));
        assertThat(service.verify(token, workload).bootFence()).isEqualTo(901L);
        Path outside = directory.resolve("outside.json");
        Files.writeString(outside, keyring());
        Files.delete(keys);
        Files.createSymbolicLink(keys, outside);
        unavailable(() -> service.verify(token, workload));
    }

    private Path file(String json) throws Exception {
        Path file = directory.resolve("keys.json");
        Files.writeString(file, json);
        return file;
    }

    private static DdcConfigDeliveryTokenService service(Path keys, Instant time) {
        return new DdcConfigDeliveryTokenService(keys, Clock.fixed(time, ZoneOffset.UTC), Duration.ofSeconds(60));
    }

    private static DdcConfigWorkloadIdentityVO workload(String boot, String actor, long targetRevision) {
        return new DdcConfigWorkloadIdentityVO(new DdcTenantContextDTO(1001L, "7001", actor),
                new DdcConfigScopeVO("1001", "reading", "prod", "reader", "application.yml"), "reader-prod", targetRevision,
                "cluster-prod", "reader", "workload-a", "pod-a", "application", "containerd://one", boot);
    }

    private static DdcConfigWorkloadIdentityVO changed(DdcConfigWorkloadIdentityVO value, String field) {
        return new DdcConfigWorkloadIdentityVO(field.equals("identityTenantId") ? new DdcTenantContextDTO(1001L, "7002", "service:reader") : value.tenant(),
                field.equals("scope") ? new DdcConfigScopeVO("1001", "reading", "staging", "reader", "application.yml") : value.scope(),
                field.equals("targetBindingId") ? "other-target" : value.targetBindingId(), field.equals("targetBindingRevision") ? 4 : value.targetBindingRevision(),
                field.equals("clusterId") ? "other-cluster" : value.clusterId(), field.equals("k8sNamespace") ? "other-ns" : value.k8sNamespace(),
                field.equals("workloadUid") ? "other-workload" : value.workloadUid(), field.equals("podUid") ? "other-pod" : value.podUid(),
                field.equals("containerName") ? "worker" : value.containerName(), field.equals("containerId") ? "containerd://two" : value.containerId(),
                field.equals("bootId") ? "boot-b" : value.bootId());
    }

    private static DdcConfigSnapshotDTO snapshot(DdcConfigScopeVO scope, Instant until) {
        var draft = new DdcConfigSnapshotDTO("1", scope, "43", true, "sha256:" + "a".repeat(64), "sha256:" + "0".repeat(64),
                Map.of("enabled", BooleanNode.TRUE), null, until);
        return new DdcConfigSnapshotDTO("1", scope, "43", true, draft.getRestartDigest(), DdcConfigCanonicalJson.contentSha256(draft),
                draft.getHotValues(), null, until);
    }

    private static String keyring(long version, String active, String... keys) {
        return """
                {"schemaVersion":"1","keyringVersion":"%d","activeKeyId":"%s","keys":[%s]}
                """.formatted(version, active, String.join(",", keys));
    }

    private static String key(String id, String secret, Instant until) {
        return """
                {"keyId":"%s","secretBase64Url":"%s","validUntil":"%s"}
                """.formatted(id, Base64.getUrlEncoder().withoutPadding().encodeToString(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)), until);
    }

    private static String signed(byte[] payload) throws Exception {
        String input = "dct1.key-a." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY_A.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        return input + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(input.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
    }

    private static void rejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isExactlyInstanceOf(DdcConfigDeliveryTokenService.Rejected.class)
                .hasMessage("CONFIG_DELIVERY_TOKEN_REJECTED").hasNoCause();
    }

    private static void unavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isExactlyInstanceOf(DdcConfigDeliveryTokenService.Unavailable.class)
                .hasMessage("CONFIG_DELIVERY_TOKEN_UNAVAILABLE").hasNoCause();
    }
}
