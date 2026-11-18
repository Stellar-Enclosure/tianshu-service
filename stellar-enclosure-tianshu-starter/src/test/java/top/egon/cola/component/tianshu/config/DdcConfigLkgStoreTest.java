package top.egon.cola.component.tianshu.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptCommand;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptStatusEnum;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigSnapshotDTO;
import top.egon.cola.component.tianshu.state.DdcConfigLkgStore;
import top.egon.cola.component.tianshu.state.DdcConfigLkgStore.IncarnationVO;
import top.egon.cola.component.tianshu.state.DdcConfigLkgStore.ScopeKeyVO;
import top.egon.cola.component.tianshu.state.DdcConfigLkgStore.StateVO;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Durable, credential-free local recovery contract; every file is confined to the JUnit directory. */
class DdcConfigLkgStoreTest {
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private static final String RESTART = "sha256:" + "a".repeat(64);
    private static final Instant VERIFIED = Instant.parse("2026-11-18T16:00:00Z");
    private static final IncarnationVO INCARNATION = new IncarnationVO("pod-a", "containerd://runtime-a", "boot-a");
    @TempDir Path temporary;

    @Test
    void exposesTheReadOnlyRecoveryAndAtomicStateContract() throws Exception {
        Class<?> store = Class.forName("top.egon.cola.component.tianshu.state.DdcConfigLkgStore");
        assertThat(store.getConstructor(Path.class)).isNotNull();
        assertThat(ScopeKeyVO.class.getConstructor(DdcConfigScopeVO.class, String.class, String.class, String.class,
                String.class, String.class, String.class)).isNotNull();
        assertThat(IncarnationVO.class.getConstructor(String.class, String.class, String.class)).isNotNull();
        assertThat(StateVO.class.getConstructor(ScopeKeyVO.class, long.class, String.class, String.class, String.class,
                boolean.class, DdcConfigSnapshotDTO.class, Instant.class, IncarnationVO.class, long.class, JsonNode.class)).isNotNull();
        assertThat(store.getMethod("read", ScopeKeyVO.class).getReturnType()).isEqualTo(java.util.Optional.class);
        assertThat(store.getMethod("write", StateVO.class).getReturnType()).isEqualTo(void.class);
    }

    @Test
    void absentReadDoesNotCreateDirectoryOrWriterFiles() throws Exception {
        Path absent = temporary.resolve("state");
        assertThat(new DdcConfigLkgStore(absent).read(key("1001", "prod"))).isEmpty();
        assertThat(Files.exists(absent)).isFalse();
        assertThat(files(temporary)).isEmpty();
    }

    @Test
    void atomicallyRoundTripsCompleteSnapshotAndPendingWithoutCredentialOrMutableAlias() throws Exception {
        var key = key("1001", "prod");
        var snapshot = snapshot(key, "43", true);
        ObjectNode body = receipt(snapshot, INCARNATION, 2, VERIFIED);
        var state = state(key, snapshot, INCARNATION, 2, body, null, false);
        body.put("deliveryToken", "must-not-be-retained");
        var store = new DdcConfigLkgStore(temporary.resolve("state"));
        store.write(state);
        var restored = store.read(key).orElseThrow();
        assertThat(restored.key()).isEqualTo(key);
        assertThat(restored.bindingRevision()).isEqualTo(3);
        assertThat(restored.applied().getHotValues()).isEqualTo(snapshot.getHotValues());
        assertThat(restored.applied().getValidUntil()).isEqualTo(snapshot.getValidUntil());
        assertThat(restored.reportIncarnation()).isEqualTo(INCARNATION);
        assertThat(restored.lastReportSequence()).isEqualTo(2);
        assertThat(restored.pendingReceipt()).isEqualTo(receipt(snapshot, INCARNATION, 2, VERIFIED));
        ((ObjectNode) restored.pendingReceipt()).put("deliveryToken", "mutated-copy");
        assertThat(restored.pendingReceipt().has("deliveryToken")).isFalse();
        assertThat(Files.readString(stateFile(temporary.resolve("state"))))
                .doesNotContain("deliveryToken", "must-not-be-retained", "mutated-copy");
        assertThat(Files.getPosixFilePermissions(temporary.resolve("state")))
                .isEqualTo(PosixFilePermissions.fromString("rwx------"));
        for (Path file : files(temporary.resolve("state"))) {
            assertThat(Files.getPosixFilePermissions(file)).isEqualTo(PosixFilePermissions.fromString("rw-------"));
        }
        assertThat(state.toString()).doesNotContain("runtime-a", "receiptDigest", "flags.enabled");
    }

    @Test
    void scopePartitionsDistinguishTenantEnvironmentAndStructuredDelimiterValues() {
        var root = temporary.resolve("state");
        var store = new DdcConfigLkgStore(root);
        var first = key("1001", "prod");
        store.write(state(first, snapshot(first, "43", true), null, 0, null, null, false));
        assertThat(store.read(key("1002", "prod"))).isEmpty();
        assertThat(store.read(key("1001", "stage"))).isEmpty();
        var delimiter = new ScopeKeyVO(new DdcConfigScopeVO("1001", "reading|prod", "x", "reader", "application.yml"),
                "42", "reader-prod", "cluster-prod", "apps-prod", "workload-a", "application");
        var another = new ScopeKeyVO(new DdcConfigScopeVO("1001", "reading", "prod|x", "reader", "application.yml"),
                "42", "reader-prod", "cluster-prod", "apps-prod", "workload-a", "application");
        store.write(state(delimiter, snapshot(delimiter, "44", true), null, 0, null, null, false));
        assertThat(store.read(another)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"BEFORE_FILE_FORCE", "BEFORE_ATOMIC_MOVE"})
    void failureBeforeReplaceLeavesOldStateAndReleasesAllWriterResources(String phase) throws Exception {
        var key = key("1001", "prod");
        Path directory = temporary.resolve("state");
        var store = new DdcConfigLkgStore(directory);
        store.write(state(key, snapshot(key, "43", true), null, 0, null, null, false));
        var failing = failingStore(directory, phase);
        assertThatThrownBy(() -> failing.write(state(key, snapshot(key, "44", false), null, 0, null, "44", false)))
                .isInstanceOf(DdcConfigLkgStore.LkgStoreException.class)
                .satisfies(error -> assertThat(((DdcConfigLkgStore.LkgStoreException) error).durabilityIndeterminate()).isFalse())
                .hasMessage("CONFIG_LKG_WRITE_FAILED").hasNoCause();
        assertThat(store.read(key).orElseThrow().applied().getReleaseRevision()).isEqualTo("43");
        assertThat(files(directory)).noneMatch(path -> path.toString().endsWith(".tmp"));
        store.write(state(key, snapshot(key, "44", false), null, 0, null, "44", false));
        assertThat(store.read(key).orElseThrow().disabledRevision()).isEqualTo("44");
    }

    @ParameterizedTest
    @ValueSource(strings = {"AFTER_ATOMIC_MOVE", "BEFORE_DIRECTORY_FORCE"})
    void failureAfterReplaceIsExplicitlyIndeterminateAndNeverPretendsDiskRollback(String phase) throws Exception {
        var key = key("1001", "prod");
        Path directory = temporary.resolve("state");
        var store = new DdcConfigLkgStore(directory);
        store.write(state(key, snapshot(key, "43", true), null, 0, null, null, false));
        assertThatThrownBy(() -> failingStore(directory, phase).write(
                state(key, snapshot(key, "44", false), null, 0, null, "44", false)))
                .isInstanceOf(DdcConfigLkgStore.LkgStoreException.class)
                .hasMessage("CONFIG_LKG_DURABILITY_UNKNOWN").hasNoCause()
                .satisfies(error -> assertThat(((DdcConfigLkgStore.LkgStoreException) error).durabilityIndeterminate()).isTrue());
        // Visibility is observable here; it is not proof of persistence through host power loss.
        assertThat(store.read(key).orElseThrow().disabledRevision()).isEqualTo("44");
        assertThat(files(directory)).noneMatch(path -> path.toString().endsWith(".tmp"));
    }

    @Test
    void corruptAggregateRequiresIndependentVerifiedSnapshotRecovery() throws Exception {
        var key = key("1001", "prod");
        var snapshot = snapshot(key, "43", true);
        var state = state(key, snapshot, INCARNATION, 1, receipt(snapshot, INCARNATION, 1, VERIFIED), null, false);
        Path directory = temporary.resolve("state");
        var store = new DdcConfigLkgStore(directory);
        store.write(state);
        Files.writeString(stateFile(directory), "{\"corrupt\":\"sensitive-invalid-fixture\"}");
        assertThatThrownBy(() -> store.read(key)).hasMessage("CONFIG_LKG_INVALID").hasNoCause();
        assertThatThrownBy(() -> store.write(state)).hasMessage("CONFIG_LKG_INVALID");
        store.writeAfterVerifiedSnapshot(state);
        assertThat(store.read(key).orElseThrow().applied().getReleaseRevision()).isEqualTo("43");
    }

    @Test
    void checksAggregateChecksumAndRejectsCrossScopeFileSubstitution() throws Exception {
        var first = key("1001", "prod");
        var second = key("1002", "prod");
        Path directory = temporary.resolve("state");
        var store = new DdcConfigLkgStore(directory);
        store.write(state(first, snapshot(first, "43", true), null, 0, null, null, false));
        Path firstFile = stateFile(directory);
        String firstBody = Files.readString(firstFile);
        store.write(state(second, snapshot(second, "43", true), null, 0, null, null, false));
        Path secondFile = files(directory).stream().filter(path -> path.toString().endsWith(".json") && !path.equals(firstFile)).findFirst().orElseThrow();
        Files.writeString(secondFile, firstBody);
        assertThatThrownBy(() -> store.read(second)).hasMessage("CONFIG_LKG_SCOPE_MISMATCH");
        Files.writeString(firstFile, firstBody.replace("\"isolated\":false", "\"isolated\":true"));
        assertThatThrownBy(() -> store.read(first)).hasMessage("CONFIG_LKG_INVALID");
    }

    @Test
    void persistsIsolationAndDisableFloorWithoutRevivingOldEnabledData() {
        var key = key("1001", "prod");
        var store = new DdcConfigLkgStore(temporary.resolve("state"));
        store.write(state(key, snapshot(key, "43", true), null, 0, null, "44", true));
        var persisted = store.read(key).orElseThrow();
        assertThat(persisted.isolated()).isTrue();
        assertThat(persisted.disabledRevision()).isEqualTo("44");
        // The old applied fact is retained for diagnostics; the cold reader must reject it against the floor.
        assertThat(persisted.applied().getReleaseRevision()).isEqualTo("43");
        assertThatThrownBy(() -> store.write(state(key, snapshot(key, "43", true), null, 0, null, null, false)))
                .hasMessage("CONFIG_LKG_VERIFICATION_REQUIRED");
        assertThatThrownBy(() -> store.writeAfterVerifiedSnapshot(state(key, snapshot(key, "43", true),
                INCARNATION, 0, null, null, false))).hasMessage("CONFIG_LKG_FENCE_REGRESSION");
        assertThatThrownBy(() -> store.write(state(key, snapshot(key, "44", false), INCARNATION, 0, null, "44", false)))
                .hasMessage("CONFIG_LKG_VERIFICATION_REQUIRED");
        store.writeAfterVerifiedSnapshot(state(key, snapshot(key, "44", false), INCARNATION, 0, null, "44", false));
        assertThat(store.read(key).orElseThrow().applied().getHotValues()).isEmpty();
    }

    @Test
    void sequencesAreMonotonicAndPendingRetryIsExactWithinOneIncarnation() {
        var key = key("1001", "prod");
        var snapshot = snapshot(key, "43", true);
        var store = new DdcConfigLkgStore(temporary.resolve("state"));
        var same = state(key, snapshot, INCARNATION, 2, receipt(snapshot, INCARNATION, 2, VERIFIED), null, false);
        store.write(same); store.write(same);
        assertThatThrownBy(() -> store.write(state(key, snapshot, INCARNATION, 1,
                receipt(snapshot, INCARNATION, 1, VERIFIED), null, false))).hasMessage("CONFIG_LKG_SEQUENCE_REGRESSION");
        assertThatThrownBy(() -> store.write(state(key, snapshot, INCARNATION, 2,
                receipt(snapshot, INCARNATION, 2, VERIFIED.plusSeconds(1)), null, false))).hasMessage("CONFIG_LKG_SEQUENCE_CONFLICT");
        store.write(state(key, snapshot, INCARNATION, 2, null, null, false));
        assertThatThrownBy(() -> store.write(same)).hasMessage("CONFIG_LKG_SEQUENCE_CONFLICT");
        var newBoot = new IncarnationVO("pod-a", "containerd://runtime-b", "boot-b");
        store.write(state(key, snapshot, newBoot, 0, null, null, false));
        assertThat(store.read(key).orElseThrow().lastReportSequence()).isZero();
        assertThat(store.read(key).orElseThrow().pendingReceipt()).isNull();
    }

    @Test
    void nonSuccessCanReportAnAppliedBaselineWithoutPersistingItAsRecoverableContent() {
        var key = key("1001", "prod");
        var oldBaseline = snapshot(key, "1", true);
        var target = snapshot(key, "43", true);
        var command = DdcConfigReceiptCommand.builder().schemaVersion("1").bootId(INCARNATION.bootId())
                .releaseRevision("43").reportSeq("1").contentSha256(target.getContentSha256())
                .appliedRevision("1").appliedContentSha256(oldBaseline.getContentSha256())
                .baselineRestartDigest(RESTART).status(DdcConfigReceiptStatusEnum.RESTART_REQUIRED)
                .reportedAt(VERIFIED).appliedAt(VERIFIED).errorCode("CONFIG_RESTART_REQUIRED").build();
        command.setReceiptDigest(DdcConfigCanonicalJson.receiptDigest(command));
        ObjectNode body = MAPPER.valueToTree(command); body.remove("deliveryToken");
        var state = new StateVO(key, 3, RESTART, "pod-a", "43", false, null, null, INCARNATION, 1, body);
        var store = new DdcConfigLkgStore(temporary.resolve("state"));
        store.write(state);
        var restored = store.read(key).orElseThrow();
        assertThat(restored.applied()).isNull();
        assertThat(restored.pendingReceipt().get("appliedRevision").textValue()).isEqualTo("1");
        assertThat(restored.pendingReceipt().get("appliedContentSha256").textValue()).isEqualTo(oldBaseline.getContentSha256());
        assertThatThrownBy(() -> state(key, target, INCARNATION, 1, body, null, false))
                .hasMessage("CONFIG_LKG_PENDING_INVALID");
        var success = receipt(target, INCARNATION, 1, VERIFIED);
        assertThatThrownBy(() -> state(key, null, INCARNATION, 1, success, null, false))
                .hasMessage("CONFIG_LKG_PENDING_INVALID");
        command.setAppliedRevision("0"); command.setReceiptDigest(DdcConfigCanonicalJson.receiptDigest(command));
        ObjectNode invalidRevision = MAPPER.valueToTree(command); invalidRevision.remove("deliveryToken");
        assertThatThrownBy(() -> state(key, null, INCARNATION, 1, invalidRevision, null, false)).hasMessage("CONFIG_LKG_INVALID");
        command.setAppliedRevision("1").setAppliedContentSha256("invalid-digest");
        command.setReceiptDigest(DdcConfigCanonicalJson.receiptDigest(command));
        ObjectNode invalidDigest = MAPPER.valueToTree(command); invalidDigest.remove("deliveryToken");
        assertThatThrownBy(() -> state(key, null, INCARNATION, 1, invalidDigest, null, false)).hasMessage("CONFIG_LKG_INVALID");
    }

    @Test
    void excludesUnknownCredentialFieldsAndRejectsChangedReceiptOrSnapshotDigest() {
        var key = key("1001", "prod");
        var snapshot = snapshot(key, "43", true);
        var body = receipt(snapshot, INCARNATION, 1, VERIFIED);
        body.put("deliveryToken", "synthetic-secret");
        assertThatThrownBy(() -> state(key, snapshot, INCARNATION, 1, body, null, false)).hasNoCause();
        var changed = receipt(snapshot, INCARNATION, 1, VERIFIED); changed.put("reportSeq", "2");
        assertThatThrownBy(() -> state(key, snapshot, INCARNATION, 1, changed, null, false)).hasMessage("CONFIG_LKG_PENDING_INVALID");
        var corrupt = new DdcConfigSnapshotDTO("1", key.scope(), "43", true, RESTART, "sha256:" + "b".repeat(64),
                snapshot.getHotValues(), null, snapshot.getValidUntil());
        assertThatThrownBy(() -> state(key, corrupt, null, 0, null, null, false)).hasMessage("CONFIG_LKG_APPLIED_INVALID");
    }

    @Test
    void rejectsSymlinkDirectoryAndSymlinkOrBroadlyReadableStateEvenForVerifiedRecovery() throws Exception {
        Path real = temporary.resolve("real"); Files.createDirectory(real, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Path link = temporary.resolve("link"); Files.createSymbolicLink(link, real);
        var key = key("1001", "prod");
        assertThatThrownBy(() -> new DdcConfigLkgStore(link).read(key)).hasMessage("CONFIG_LKG_PATH_UNSAFE");
        var store = new DdcConfigLkgStore(real);
        var snapshot = snapshot(key, "43", true);
        var state = state(key, snapshot, INCARNATION, 1, receipt(snapshot, INCARNATION, 1, VERIFIED), null, false);
        store.write(state);
        Path file = stateFile(real);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
        assertThatThrownBy(() -> store.read(key)).hasMessage("CONFIG_LKG_PERMISSIONS_UNSAFE");
        assertThatThrownBy(() -> store.writeAfterVerifiedSnapshot(state)).hasMessage("CONFIG_LKG_PERMISSIONS_UNSAFE");
        Files.delete(file);
        Path outside = temporary.resolve("outside"); Files.writeString(outside, "unchanged");
        Files.createSymbolicLink(file, outside);
        assertThatThrownBy(() -> store.read(key)).hasMessage("CONFIG_LKG_PATH_UNSAFE");
        assertThatThrownBy(() -> store.writeAfterVerifiedSnapshot(state)).hasMessage("CONFIG_LKG_PATH_UNSAFE");
        assertThat(Files.readString(outside)).isEqualTo("unchanged");
    }

    @Test
    void writerLockExcludesAConcurrentWriterAndIsReusableAfterRelease() throws Exception {
        var key = key("1001", "prod");
        Path directory = temporary.resolve("state");
        var store = new DdcConfigLkgStore(directory);
        var state = state(key, snapshot(key, "43", true), null, 0, null, null, false);
        store.write(state);
        Path lock = files(directory).stream().filter(path -> path.toString().endsWith(".lock")).findFirst().orElseThrow();
        try (var channel = FileChannel.open(lock, StandardOpenOption.WRITE); var ignored = channel.lock()) {
            assertThatThrownBy(() -> new DdcConfigLkgStore(directory).write(state)).hasMessage("CONFIG_LKG_WRITER_BUSY");
        }
        store.write(state);
        assertThat(store.read(key)).isPresent();
    }

    @Test
    void boundsDiskInputAndOutputWithoutLeakingTheRejectedBody() throws Exception {
        var key = key("1001", "prod");
        var store = new DdcConfigLkgStore(temporary.resolve("state"));
        var values = Map.<String, JsonNode>of("large", MAPPER.getNodeFactory().textNode("x".repeat(2 * 1024 * 1024)));
        var draft = new DdcConfigSnapshotDTO("1", key.scope(), "43", true, RESTART, RESTART, values, null, VERIFIED.plusSeconds(600));
        var oversized = new DdcConfigSnapshotDTO("1", key.scope(), "43", true, RESTART,
                DdcConfigCanonicalJson.contentSha256(draft), values, null, draft.getValidUntil());
        assertThatThrownBy(() -> store.write(state(key, oversized, null, 0, null, null, false)))
                .hasMessage("CONFIG_LKG_TOO_LARGE").hasNoCause();
        assertThat(Files.exists(temporary.resolve("state"))).isFalse();
        store.write(state(key, snapshot(key, "43", true), null, 0, null, null, false));
        Files.writeString(stateFile(temporary.resolve("state")), "x".repeat(2 * 1024 * 1024 + 1));
        assertThatThrownBy(() -> store.read(key)).hasMessage("CONFIG_LKG_INVALID").hasNoCause();
    }

    private static ScopeKeyVO key(String tenant, String env) {
        return new ScopeKeyVO(new DdcConfigScopeVO(tenant, "reading", env, "reader", "application.yml"),
                "42", "reader-prod", "cluster-prod", "apps-prod", "workload-a", "application");
    }

    private static DdcConfigSnapshotDTO snapshot(ScopeKeyVO key, String revision, boolean enabled) {
        Map<String, JsonNode> values = enabled ? Map.of("flags.enabled", MAPPER.getNodeFactory().booleanNode(true)) : Map.of();
        var draft = new DdcConfigSnapshotDTO("1", key.scope(), revision, enabled, RESTART, RESTART, values, null, VERIFIED.plusSeconds(600));
        return new DdcConfigSnapshotDTO("1", key.scope(), revision, enabled, RESTART,
                DdcConfigCanonicalJson.contentSha256(draft), values, null, draft.getValidUntil());
    }

    private static StateVO state(ScopeKeyVO key, DdcConfigSnapshotDTO snapshot, IncarnationVO incarnation,
            long sequence, JsonNode pending, String disabledRevision, boolean isolated) {
        return new StateVO(key, 3, RESTART, "pod-a", disabledRevision, isolated, snapshot, VERIFIED, incarnation, sequence, pending);
    }

    private static ObjectNode receipt(DdcConfigSnapshotDTO snapshot, IncarnationVO incarnation, long sequence, Instant at) {
        var receipt = DdcConfigReceiptCommand.builder().schemaVersion("1").bootId(incarnation.bootId())
                .releaseRevision(snapshot.getReleaseRevision()).reportSeq(Long.toString(sequence))
                .appliedRevision(snapshot.getReleaseRevision()).contentSha256(snapshot.getContentSha256())
                .appliedContentSha256(snapshot.getContentSha256()).baselineRestartDigest(RESTART)
                .status(snapshot.getEnabled() ? DdcConfigReceiptStatusEnum.APPLIED : DdcConfigReceiptStatusEnum.DISABLED_APPLIED)
                .reportedAt(at).appliedAt(at).errorCode(null).errorMessage(null).build();
        receipt.setReceiptDigest(DdcConfigCanonicalJson.receiptDigest(receipt));
        ObjectNode node = MAPPER.valueToTree(receipt);
        node.remove("deliveryToken");
        return node;
    }

    private static DdcConfigLkgStore failingStore(Path directory, String phase) throws Exception {
        var constructor = DdcConfigLkgStore.class.getDeclaredConstructor(Path.class, Consumer.class);
        constructor.setAccessible(true);
        Consumer<String> probe = current -> {
            if (current.equals(phase)) { throw new UncheckedIOException(new IOException("private-fault-fixture")); }
        };
        return constructor.newInstance(directory, probe);
    }

    private static List<Path> files(Path directory) throws IOException {
        try (var stream = Files.list(directory)) { return stream.toList(); }
    }

    private static Path stateFile(Path directory) throws IOException {
        return files(directory).stream().filter(path -> path.toString().endsWith(".json")).findFirst().orElseThrow();
    }
}
