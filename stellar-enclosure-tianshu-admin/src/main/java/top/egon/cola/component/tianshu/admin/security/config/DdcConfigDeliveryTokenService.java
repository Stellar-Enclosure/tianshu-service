package top.egon.cola.component.tianshu.admin.security.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import top.egon.cola.component.tianshu.admin.model.vo.DdcConfigWorkloadIdentityVO;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigSnapshotDTO;

/** Short-lived, incarnation-bound delivery proofs; authentication and durable boot confirmation precede issuance. */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class DdcConfigDeliveryTokenService {

    private static final int MAX_KEYRING_BYTES = 65_536;
    private static final int MAX_PAYLOAD_BYTES = 4096;
    private static final int MAX_TOKEN_LENGTH = 8192;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final Set<String> CLAIM_FIELDS = Set.of("schemaVersion", "bootFence", "configId", "releaseRevision",
            "enabled", "contentSha256", "restartDigest", "scope", "identityTenantId", "targetBindingId",
            "targetBindingRevision", "clusterId", "k8sNamespace", "workloadUid", "podUid", "containerName",
            "containerId", "bootId", "issuedAt", "expiresAt");
    private static final Set<String> SCOPE_FIELDS = Set.of("tenantId", "bizCode", "env", "appCode", "resourceName");

    private final SettingsVO settings;
    private final Clock clock;
    private final Object keyringLock = new Object();
    private KeyringVO acceptedKeyring;

    /** Projection I/O is deferred to requests so operator failures are classified as unavailable. */
    public DdcConfigDeliveryTokenService(Path signingKeyFile, Clock clock, Duration ttl) {
        this(new SettingsVO(signingKeyFile, ttl), Objects.requireNonNull(clock, "CONFIG_DELIVERY_CLOCK_REQUIRED"));
    }

    public String issue(DdcConfigWorkloadIdentityVO workload, long bootFence, long configId, DdcConfigSnapshotDTO snapshot) {
        try {
            requireWorkload(workload);
            if (bootFence <= 0 || configId <= 0 || snapshot == null || !"1".equals(snapshot.getSchemaVersion())
                    || !sameScope(workload.scope(), snapshot.getScope()) || snapshot.getEnabled() == null
                    || !DdcConfigCanonicalJson.contentSha256(snapshot).equals(snapshot.getContentSha256())) {
                throw new Unavailable();
            }
            positive(snapshot.getReleaseRevision());
            digest(snapshot.getContentSha256());
            digest(snapshot.getRestartDigest());
            KeyringVO keyring = keyring();
            KeyVO active = keyring.keys().get(keyring.activeKeyId());
            Instant now = clock.instant();
            Instant expires = minimum(now.plus(settings.ttl()), snapshot.getValidUntil(), active.validUntil());
            if (!expires.isAfter(now)) { throw new Unavailable(); }
            Claims claims = new Claims(bootFence, configId, snapshot.getReleaseRevision(), snapshot.getEnabled(),
                    snapshot.getContentSha256(), snapshot.getRestartDigest(), workload.scope(), workload.tenant().identityTenantId(),
                    workload.targetBindingId(), workload.targetBindingRevision(), workload.clusterId(), workload.k8sNamespace(),
                    workload.workloadUid(), workload.podUid(), workload.containerName(), workload.containerId(), workload.bootId(), now, expires);
            byte[] payload = DdcConfigCanonicalJson.canonicalBytes(json(claims));
            if (payload.length > MAX_PAYLOAD_BYTES) { throw new Unavailable(); }
            String input = "dct1." + keyring.activeKeyId() + "." + ENCODER.encodeToString(payload);
            byte[] signature = mac(input, active);
            try {
                return input + "." + ENCODER.encodeToString(signature);
            } finally {
                Arrays.fill(signature, (byte) 0);
            }
        } catch (Unavailable unavailable) {
            throw unavailable;
        } catch (RuntimeException invalidInput) {
            throw new Unavailable();
        }
    }

    public Claims verify(String deliveryToken, DdcConfigWorkloadIdentityVO current) {
        try {
            requireWorkload(current);
            if (deliveryToken == null || deliveryToken.length() > MAX_TOKEN_LENGTH) { throw new Rejected(); }
            String[] parts = deliveryToken.split("\\.", -1);
            if (parts.length != 4 || !"dct1".equals(parts[0]) || !keyId(parts[1]) || parts[2].length() > 5462) {
                throw new Rejected();
            }
            byte[] payload = decode(parts[2], 1, MAX_PAYLOAD_BYTES);
            byte[] signature = decode(parts[3], 32, 32);
            KeyringVO keyring = keyring();
            KeyVO key = keyring.keys().get(parts[1]);
            if (key == null) { throw new Rejected(); }
            String input = parts[0] + "." + parts[1] + "." + parts[2];
            byte[] expected = mac(input, key);
            try {
                if (!MessageDigest.isEqual(expected, signature)) { throw new Rejected(); }
            } finally {
                Arrays.fill(expected, (byte) 0);
                Arrays.fill(signature, (byte) 0);
            }
            JsonNode parsed = DdcConfigCanonicalJson.parse(payload);
            if (!MessageDigest.isEqual(payload, DdcConfigCanonicalJson.canonicalBytes(parsed))) { throw new Rejected(); }
            Claims claims = claims(parsed);
            Instant now = clock.instant();
            if (!claims.expiresAt().isAfter(now) || !key.validUntil().isAfter(now)
                    || claims.issuedAt().isAfter(now) || !claims.expiresAt().isAfter(claims.issuedAt())
                    || claims.expiresAt().isAfter(claims.issuedAt().plus(settings.ttl()))
                    || !matches(claims, current)) {
                throw new Rejected();
            }
            return claims;
        } catch (Unavailable unavailable) {
            throw unavailable;
        } catch (RuntimeException rejected) {
            throw new Rejected();
        }
    }

    private KeyringVO keyring() {
        synchronized (keyringLock) {
            byte[] bytes = null;
            try {
                bytes = readProjectedKeyring();
                JsonNode root = DdcConfigCanonicalJson.parse(bytes);
                fields(root, Set.of("schemaVersion", "keyringVersion", "activeKeyId", "keys"));
                if (!"1".equals(text(root, "schemaVersion"))) { throw new Unavailable(); }
                long version = positive(text(root, "keyringVersion"));
                String active = text(root, "activeKeyId");
                JsonNode entries = root.get("keys");
                if (!keyId(active) || !entries.isArray() || entries.isEmpty() || entries.size() > 8) { throw new Unavailable(); }
                Map<String, KeyVO> keys = new HashMap<>();
                for (JsonNode entry : entries) {
                    fields(entry, Set.of("keyId", "secretBase64Url", "validUntil"));
                    String id = text(entry, "keyId");
                    if (!keyId(id)) { throw new Unavailable(); }
                    byte[] material = decode(text(entry, "secretBase64Url"), 32, 64);
                    KeyVO key = new KeyVO(material, instant(text(entry, "validUntil")));
                    if (keys.putIfAbsent(id, key) != null) { throw new Unavailable(); }
                }
                if (!keys.containsKey(active)) { throw new Unavailable(); }
                String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(DdcConfigCanonicalJson.canonicalBytes(root)));
                if (acceptedKeyring != null && (version < acceptedKeyring.version()
                        || version == acceptedKeyring.version() && !fingerprint.equals(acceptedKeyring.fingerprint()))) {
                    throw new Unavailable();
                }
                acceptedKeyring = new KeyringVO(version, active, Map.copyOf(keys), fingerprint);
                return acceptedKeyring;
            } catch (IOException | GeneralSecurityException | RuntimeException invalidMaterial) {
                throw new Unavailable();
            } finally {
                if (bytes != null) { Arrays.fill(bytes, (byte) 0); }
            }
        }
    }

    private byte[] readProjectedKeyring() throws IOException {
        Path file = settings.signingKeyFile();
        Path root = file.getParent().toRealPath();
        // Resolve the complete AtomicWriter generation before opening; do not follow a replacement leaf link.
        Path resolved = file.toRealPath();
        if (!resolved.startsWith(root) || !Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS)) {
            throw new Unavailable();
        }
        try (var input = Files.newInputStream(resolved, LinkOption.NOFOLLOW_LINKS)) {
            byte[] contents = input.readNBytes(MAX_KEYRING_BYTES + 1);
            if (contents.length == 0 || contents.length > MAX_KEYRING_BYTES) {
                Arrays.fill(contents, (byte) 0);
                throw new Unavailable();
            }
            return contents;
        }
    }

    private static byte[] mac(String input, KeyVO key) {
        byte[] material = key.material().clone();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(material, "HmacSHA256"));
            return mac.doFinal(input.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException unavailable) {
            throw new Unavailable();
        } finally {
            Arrays.fill(material, (byte) 0);
        }
    }

    private static ObjectNode json(Claims claims) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("schemaVersion", "1");
        node.put("bootFence", Long.toString(claims.bootFence()));
        node.put("configId", Long.toString(claims.configId()));
        node.put("releaseRevision", claims.releaseRevision());
        node.put("enabled", claims.enabled());
        node.put("contentSha256", claims.contentSha256());
        node.put("restartDigest", claims.restartDigest());
        ObjectNode scope = node.putObject("scope");
        scope.put("tenantId", claims.scope().getTenantId());
        scope.put("bizCode", claims.scope().getBizCode());
        scope.put("env", claims.scope().getEnv());
        scope.put("appCode", claims.scope().getAppCode());
        scope.put("resourceName", claims.scope().getResourceName());
        node.put("identityTenantId", claims.identityTenantId());
        node.put("targetBindingId", claims.targetBindingId());
        node.put("targetBindingRevision", Long.toString(claims.targetBindingRevision()));
        node.put("clusterId", claims.clusterId());
        node.put("k8sNamespace", claims.k8sNamespace());
        node.put("workloadUid", claims.workloadUid());
        node.put("podUid", claims.podUid());
        node.put("containerName", claims.containerName());
        node.put("containerId", claims.containerId());
        node.put("bootId", claims.bootId());
        node.put("issuedAt", claims.issuedAt().toString());
        node.put("expiresAt", claims.expiresAt().toString());
        return node;
    }

    private static Claims claims(JsonNode node) {
        fields(node, CLAIM_FIELDS);
        if (!"1".equals(text(node, "schemaVersion")) || !node.get("enabled").isBoolean()) { throw new Rejected(); }
        JsonNode scope = node.get("scope");
        fields(scope, SCOPE_FIELDS);
        DdcConfigScopeVO identity = new DdcConfigScopeVO(text(scope, "tenantId"), text(scope, "bizCode"),
                text(scope, "env"), text(scope, "appCode"), text(scope, "resourceName"));
        return new Claims(positive(text(node, "bootFence")), positive(text(node, "configId")), text(node, "releaseRevision"),
                node.get("enabled").booleanValue(), text(node, "contentSha256"), text(node, "restartDigest"), identity,
                text(node, "identityTenantId"), text(node, "targetBindingId"), positive(text(node, "targetBindingRevision")),
                text(node, "clusterId"), text(node, "k8sNamespace"), text(node, "workloadUid"), text(node, "podUid"),
                text(node, "containerName"), text(node, "containerId"), text(node, "bootId"),
                instant(text(node, "issuedAt")), instant(text(node, "expiresAt")));
    }

    private static boolean matches(Claims claims, DdcConfigWorkloadIdentityVO current) {
        return sameScope(claims.scope(), current.scope())
                && claims.scope().getTenantId().equals(Long.toString(current.tenant().tenantId()))
                && claims.identityTenantId().equals(current.tenant().identityTenantId())
                && claims.targetBindingId().equals(current.targetBindingId())
                && claims.targetBindingRevision() == current.targetBindingRevision()
                && claims.clusterId().equals(current.clusterId()) && claims.k8sNamespace().equals(current.k8sNamespace())
                && claims.workloadUid().equals(current.workloadUid()) && claims.podUid().equals(current.podUid())
                && claims.containerName().equals(current.containerName()) && claims.containerId().equals(current.containerId())
                && claims.bootId().equals(current.bootId());
    }

    private static void requireWorkload(DdcConfigWorkloadIdentityVO workload) {
        if (workload == null || workload.tenant() == null || workload.scope() == null
                || !workload.scope().getTenantId().equals(Long.toString(workload.tenant().tenantId()))
                || workload.targetBindingRevision() <= 0) { throw new Rejected(); }
        opaque(workload.tenant().identityTenantId(), 128);
        opaque(workload.targetBindingId(), 128);
        opaque(workload.clusterId(), 64);
        dns(workload.k8sNamespace());
        opaque(workload.workloadUid(), 64);
        opaque(workload.podUid(), 64);
        dns(workload.containerName());
        opaque(workload.containerId(), 256);
        opaque(workload.bootId(), 64);
    }

    private static boolean sameScope(DdcConfigScopeVO first, DdcConfigScopeVO second) {
        return first != null && second != null && first.getTenantId().equals(second.getTenantId())
                && first.getBizCode().equals(second.getBizCode()) && first.getEnv().equals(second.getEnv())
                && first.getAppCode().equals(second.getAppCode()) && first.getResourceName().equals(second.getResourceName());
    }

    private static byte[] decode(String encoded, int minimum, int maximum) {
        if (encoded == null || encoded.length() > ((maximum + 2) / 3) * 4 || !encoded.matches("[A-Za-z0-9_-]+")) {
            throw new Rejected();
        }
        byte[] decoded = DECODER.decode(encoded);
        if (decoded.length < minimum || decoded.length > maximum || !ENCODER.encodeToString(decoded).equals(encoded)) {
            Arrays.fill(decoded, (byte) 0);
            throw new Rejected();
        }
        return decoded;
    }

    private static boolean keyId(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
    }

    private static void fields(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject() || node.size() != expected.size()) { throw new Rejected(); }
        node.fieldNames().forEachRemaining(name -> { if (!expected.contains(name)) { throw new Rejected(); } });
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) { throw new Rejected(); }
        return value.textValue();
    }

    private static long positive(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) { throw new Rejected(); }
        try { return Long.parseLong(value); }
        catch (NumberFormatException invalid) { throw new Rejected(); }
    }

    private static void digest(String value) {
        if (value == null || !value.matches("sha256:[0-9a-f]{64}")) { throw new Rejected(); }
    }

    private static void opaque(String value, int maximum) {
        if (value == null || value.isEmpty() || value.length() > maximum
                || !value.chars().allMatch(character -> character >= 0x21 && character <= 0x7e)) { throw new Rejected(); }
    }

    private static void dns(String value) {
        if (value == null || value.length() > 63 || !value.matches("[a-z0-9]([a-z0-9-]*[a-z0-9])?")) { throw new Rejected(); }
    }

    private static Instant instant(String value) {
        try {
            if (value == null || value.length() > 40 || !value.endsWith("Z")) { throw new Rejected(); }
            Instant parsed = Instant.parse(value);
            if (!parsed.toString().equals(value)) { throw new Rejected(); }
            return parsed;
        } catch (DateTimeParseException invalid) { throw new Rejected(); }
    }

    private static Instant minimum(Instant first, Instant second, Instant third) {
        Instant result = first.isBefore(second) ? first : second;
        return result.isBefore(third) ? result : third;
    }

    public record Claims(long bootFence, long configId, String releaseRevision, boolean enabled, String contentSha256,
            String restartDigest, DdcConfigScopeVO scope, String identityTenantId, String targetBindingId,
            long targetBindingRevision, String clusterId, String k8sNamespace, String workloadUid, String podUid,
            String containerName, String containerId, String bootId, Instant issuedAt, Instant expiresAt) {
        public Claims {
            if (bootFence <= 0 || configId <= 0 || targetBindingRevision <= 0 || scope == null || issuedAt == null || expiresAt == null) {
                throw new Rejected();
            }
            positive(releaseRevision);
            digest(contentSha256);
            digest(restartDigest);
            opaque(identityTenantId, 128);
            opaque(targetBindingId, 128);
            opaque(clusterId, 64);
            dns(k8sNamespace);
            opaque(workloadUid, 64);
            opaque(podUid, 64);
            dns(containerName);
            opaque(containerId, 256);
            opaque(bootId, 64);
        }
    }

    public static final class Rejected extends IllegalStateException {
        public Rejected() { super("CONFIG_DELIVERY_TOKEN_REJECTED"); }
    }

    public static final class Unavailable extends IllegalStateException {
        public Unavailable() { super("CONFIG_DELIVERY_TOKEN_UNAVAILABLE"); }
    }

    private record SettingsVO(Path signingKeyFile, Duration ttl) {
        private SettingsVO {
            if (signingKeyFile == null || !signingKeyFile.isAbsolute() || signingKeyFile.getParent() == null
                    || !signingKeyFile.normalize().equals(signingKeyFile) || ttl == null || ttl.getNano() != 0
                    || ttl.compareTo(Duration.ofSeconds(1)) < 0 || ttl.compareTo(Duration.ofMinutes(5)) > 0) {
                throw new IllegalArgumentException("CONFIG_DELIVERY_TOKEN_SETTINGS_INVALID");
            }
        }
    }

    private record KeyVO(byte[] material, Instant validUntil) { }
    private record KeyringVO(long version, String activeKeyId, Map<String, KeyVO> keys, String fingerprint) { }
}
