package top.egon.cola.component.tianshu.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.lang.Nullable;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptCommand;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptStatusEnum;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigSnapshotDTO;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** One bounded, credential-free aggregate per scope, atomically replaced under an exclusive writer lock. */
@Slf4j
public final class DdcConfigLkgStore {
    private static final int MAX_STATE_BYTES = 2 * 1024 * 1024;
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = PosixFilePermissions.fromString("rw-------");
    private static final Set<String> STATE_FIELDS = Set.of("schemaVersion", "key", "bindingRevision", "restartDigest",
            "podUid", "disabledRevision", "isolated", "applied", "lastVerifiedAt", "reportIncarnation",
            "lastReportSequence", "pendingReceipt", "stateSha256");
    private static final Set<String> KEY_FIELDS = Set.of("scope", "configId", "bindingId", "clusterId",
            "kubernetesNamespace", "workloadUid", "containerName");
    private static final Set<String> PENDING_FIELDS = Set.of("schemaVersion", "bootId", "releaseRevision", "reportSeq",
            "appliedRevision", "contentSha256", "appliedContentSha256", "baselineRestartDigest", "status",
            "reportedAt", "appliedAt", "errorCode", "errorMessage", "receiptDigest");
    private static final ObjectMapper MAPPER = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private final Path directory;
    private final Consumer<String> commitProbe;

    /** The directory is deployment-owned; reading an absent directory never creates it. */
    public DdcConfigLkgStore(Path stateDirectory) {
        this(stateDirectory, ignored -> { });
    }

    // Deterministic durability fault injection for package tests; never exposed as runtime configuration.
    DdcConfigLkgStore(Path stateDirectory, Consumer<String> commitProbe) {
        if (stateDirectory == null || !stateDirectory.isAbsolute()
                || !stateDirectory.normalize().equals(stateDirectory) || stateDirectory.getParent() == null) {
            throw failure("CONFIG_LKG_PATH_UNSAFE", false);
        }
        try {
            // Resolve trusted ancestor links once (including Darwin's /var), then pin future operations there.
            Path anchor = stateDirectory.getParent();
            while (!Files.exists(anchor, LinkOption.NOFOLLOW_LINKS)) { anchor = anchor.getParent(); }
            this.directory = anchor.toRealPath().resolve(anchor.relativize(stateDirectory));
        } catch (IOException | RuntimeException invalid) {
            throw failure("CONFIG_LKG_PATH_UNSAFE", false);
        }
        this.commitProbe = Objects.requireNonNull(commitProbe, "commitProbe");
    }

    /** Validates persisted facts only; authorization, baseline compatibility and freshness belong to the caller. */
    public Optional<StateVO> read(@Valid @NotNull ScopeKeyVO key) {
        Objects.requireNonNull(key, "key");
        byte[] bytes = null;
        try {
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) { return Optional.empty(); }
            Path root = checkedDirectory(false);
            Path file = root.resolve(fileName(key));
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) { return Optional.empty(); }
            checkFile(file);
            try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                bytes = input.readNBytes(MAX_STATE_BYTES + 1);
            }
            if (bytes.length == 0 || bytes.length > MAX_STATE_BYTES) { throw failure("CONFIG_LKG_INVALID", false); }
            JsonNode json = DdcConfigCanonicalJson.parse(bytes);
            fields(json, STATE_FIELDS);
            if (!"1".equals(text(json, "schemaVersion", false))) { throw failure("CONFIG_LKG_INVALID", false); }
            var payload = ((ObjectNode) json).deepCopy();
            String checksum = text(payload, "stateSha256", false);
            payload.remove("stateSha256");
            if (!hash(payload).equals(checksum)) { throw failure("CONFIG_LKG_INVALID", false); }
            StateVO state = decode(payload);
            if (!key.equals(state.key())) { throw failure("CONFIG_LKG_SCOPE_MISMATCH", false); }
            return Optional.of(state);
        } catch (LkgStoreException known) {
            throw known;
        } catch (IOException invalid) {
            throw failure("CONFIG_LKG_READ_FAILED", false);
        } catch (RuntimeException invalid) {
            // Parser/validation causes may retain complete content. Do not expose them through the exception.
            throw failure("CONFIG_LKG_INVALID", false);
        } finally {
            if (bytes != null) { Arrays.fill(bytes, (byte) 0); }
        }
    }

    /** Writes the whole result and pending receipt together; no success is returned before directory force. */
    public void write(@Valid @NotNull StateVO state) {
        writeInternal(state, false);
    }

    /** Only an independently authenticated full 200 for this exact scope may replace corrupt prior state. */
    public void writeAfterVerifiedSnapshot(@Valid @NotNull StateVO state) {
        Objects.requireNonNull(state, "state");
        if (state.lastVerifiedAt() == null || state.reportIncarnation() == null) {
            throw failure("CONFIG_LKG_VERIFICATION_REQUIRED", false);
        }
        writeInternal(state, true);
    }

    private void writeInternal(StateVO state, boolean verifiedRecovery) {
        Objects.requireNonNull(state, "state");
        byte[] bytes = encode(state);
        Path temporary = null;
        boolean replaced = false;
        try {
            Path root = checkedDirectory(true);
            Path lockPath = root.resolve(fileName(state.key()) + ".lock");
            try (var channel = FileChannel.open(lockPath, Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS), PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS))) {
                checkFile(lockPath);
                try (var lock = channel.tryLock()) {
                    if (lock == null) { throw failure("CONFIG_LKG_WRITER_BUSY", false); }
                    Optional<StateVO> previous;
                    try { previous = read(state.key()); }
                    catch (LkgStoreException invalid) {
                        if (!verifiedRecovery || !Set.of("CONFIG_LKG_INVALID", "CONFIG_LKG_SCOPE_MISMATCH",
                                "CONFIG_LKG_APPLIED_INVALID", "CONFIG_LKG_PENDING_INVALID").contains(invalid.code())) { throw invalid; }
                        previous = Optional.empty();
                    }
                    previous.ifPresent(value -> checkAdvance(value, state, verifiedRecovery));
                    Path target = root.resolve(fileName(state.key()));
                    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) { checkFile(target); }
                    temporary = root.resolve("." + UUID.randomUUID() + ".tmp");
                    try (var output = FileChannel.open(temporary, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                            LinkOption.NOFOLLOW_LINKS), PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS))) {
                        ByteBuffer buffer = ByteBuffer.wrap(bytes);
                        while (buffer.hasRemaining()) { output.write(buffer); }
                        commitProbe.accept("BEFORE_FILE_FORCE");
                        output.force(true);
                    }
                    commitProbe.accept("BEFORE_ATOMIC_MOVE");
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    replaced = true;
                    commitProbe.accept("AFTER_ATOMIC_MOVE");
                    commitProbe.accept("BEFORE_DIRECTORY_FORCE");
                    try (var parent = FileChannel.open(root, StandardOpenOption.READ)) { parent.force(true); }
                }
            }
        } catch (OverlappingFileLockException busy) {
            throw failure("CONFIG_LKG_WRITER_BUSY", false);
        } catch (LkgStoreException known) {
            if (replaced) { throw failure("CONFIG_LKG_DURABILITY_UNKNOWN", true); }
            throw known;
        } catch (IOException | RuntimeException invalid) {
            throw failure(replaced ? "CONFIG_LKG_DURABILITY_UNKNOWN" : "CONFIG_LKG_WRITE_FAILED", replaced);
        } finally {
            Arrays.fill(bytes, (byte) 0);
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException ignored) { log.warn("CONFIG_LKG_TEMP_CLEANUP_FAILED"); }
            }
        }
    }

    private Path checkedDirectory(boolean create) throws IOException {
        if (create && !Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            // The deployment must provision the writable parent; never create arbitrary ancestor directories.
            Path parent = directory.getParent().toRealPath();
            Files.createDirectory(parent.resolve(directory.getFileName()), PosixFilePermissions.asFileAttribute(DIRECTORY_PERMISSIONS));
        }
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw failure("CONFIG_LKG_PATH_UNSAFE", false);
        }
        Path root = directory.toRealPath();
        checkPermissions(root, DIRECTORY_PERMISSIONS);
        return root;
    }

    private static void checkFile(Path file) throws IOException {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw failure("CONFIG_LKG_PATH_UNSAFE", false);
        }
        checkPermissions(file, FILE_PERMISSIONS);
    }

    private static void checkPermissions(Path path, Set<PosixFilePermission> expected) throws IOException {
        if (!Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS).equals(expected)) {
            throw failure("CONFIG_LKG_PERMISSIONS_UNSAFE", false);
        }
        UserPrincipal processOwner = path.getFileSystem().getUserPrincipalLookupService()
                .lookupPrincipalByName(System.getProperty("user.name"));
        if (!Files.getOwner(path, LinkOption.NOFOLLOW_LINKS).equals(processOwner)) {
            throw failure("CONFIG_LKG_OWNER_UNSAFE", false);
        }
    }

    private static void checkAdvance(StateVO previous, StateVO next, boolean verifiedRecovery) {
        if (previous.isolated() && !next.isolated() && !verifiedRecovery) {
            throw failure("CONFIG_LKG_VERIFICATION_REQUIRED", false);
        }
        if (previous.disabledRevision() != null && (next.disabledRevision() == null
                || number(next.disabledRevision(), false) < number(previous.disabledRevision(), false))) {
            throw failure("CONFIG_LKG_FENCE_REGRESSION", false);
        }
        if (Objects.equals(previous.reportIncarnation(), next.reportIncarnation())
                && previous.lastReportSequence() > next.lastReportSequence()) {
            throw failure("CONFIG_LKG_SEQUENCE_REGRESSION", false);
        }
        if (Objects.equals(previous.reportIncarnation(), next.reportIncarnation())
                && previous.lastReportSequence() == next.lastReportSequence()
                && next.pendingReceipt() != null && next.lastReportSequence() > 0
                && (previous.pendingReceipt() == null || !previous.pendingReceipt().equals(next.pendingReceipt()))) {
            throw failure("CONFIG_LKG_SEQUENCE_CONFLICT", false);
        }
    }

    private static String fileName(ScopeKeyVO key) { return hash(keyNode(key)).substring(7) + ".json"; }

    private static byte[] encode(StateVO state) {
        ObjectNode json = MAPPER.createObjectNode();
        json.put("schemaVersion", "1");
        json.set("key", keyNode(state.key()));
        json.put("bindingRevision", Long.toString(state.bindingRevision()));
        json.put("restartDigest", state.restartDigest());
        json.put("podUid", state.podUid());
        json.put("disabledRevision", state.disabledRevision());
        json.put("isolated", state.isolated());
        json.set("applied", state.applied() == null ? MAPPER.nullNode() : MAPPER.valueToTree(state.applied()));
        json.put("lastVerifiedAt", state.lastVerifiedAt() == null ? null : state.lastVerifiedAt().toString());
        json.set("reportIncarnation", state.reportIncarnation() == null ? MAPPER.nullNode() : MAPPER.valueToTree(state.reportIncarnation()));
        json.put("lastReportSequence", Long.toString(state.lastReportSequence()));
        json.set("pendingReceipt", state.pendingReceipt() == null ? MAPPER.nullNode() : state.pendingReceipt());
        json.put("stateSha256", hash(json));
        byte[] bytes = DdcConfigCanonicalJson.canonicalBytes(json);
        if (bytes.length > MAX_STATE_BYTES) { Arrays.fill(bytes, (byte) 0); throw failure("CONFIG_LKG_TOO_LARGE", false); }
        return bytes;
    }

    private static ObjectNode keyNode(ScopeKeyVO key) {
        ObjectNode node = MAPPER.createObjectNode();
        node.set("scope", MAPPER.valueToTree(key.scope()));
        node.put("configId", key.configId()); node.put("bindingId", key.bindingId()); node.put("clusterId", key.clusterId());
        node.put("kubernetesNamespace", key.kubernetesNamespace()); node.put("workloadUid", key.workloadUid());
        node.put("containerName", key.containerName());
        return node;
    }

    private static StateVO decode(ObjectNode node) throws IOException {
        JsonNode key = node.get("key"); fields(key, KEY_FIELDS);
        ScopeKeyVO scopeKey = new ScopeKeyVO(MAPPER.treeToValue(key.get("scope"), DdcConfigScopeVO.class),
                text(key, "configId", false), text(key, "bindingId", false), text(key, "clusterId", false),
                text(key, "kubernetesNamespace", false), text(key, "workloadUid", false), text(key, "containerName", false));
        JsonNode incarnation = node.get("reportIncarnation");
        IncarnationVO report = null;
        if (!incarnation.isNull()) {
            fields(incarnation, Set.of("podUid", "containerId", "bootId"));
            report = new IncarnationVO(text(incarnation, "podUid", false), text(incarnation, "containerId", false),
                    text(incarnation, "bootId", false));
        }
        if (!node.get("isolated").isBoolean()) { throw failure("CONFIG_LKG_INVALID", false); }
        return new StateVO(scopeKey, number(text(node, "bindingRevision", false), false), text(node, "restartDigest", false),
                text(node, "podUid", false), text(node, "disabledRevision", true), node.get("isolated").booleanValue(),
                node.get("applied").isNull() ? null : MAPPER.treeToValue(node.get("applied"), DdcConfigSnapshotDTO.class),
                instant(node, "lastVerifiedAt", true), report, number(text(node, "lastReportSequence", false), true),
                node.get("pendingReceipt").isNull() ? null : node.get("pendingReceipt"));
    }

    private static String hash(JsonNode value) {
        byte[] bytes = DdcConfigCanonicalJson.canonicalBytes(value);
        try { return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
        finally { Arrays.fill(bytes, (byte) 0); }
    }

    private static void fields(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject() || node.size() != expected.size()) { throw failure("CONFIG_LKG_INVALID", false); }
        node.fieldNames().forEachRemaining(field -> { if (!expected.contains(field)) { throw failure("CONFIG_LKG_INVALID", false); } });
    }

    private static String text(JsonNode node, String name, boolean nullable) {
        JsonNode value = node.get(name);
        if (value != null && value.isTextual()) { return value.textValue(); }
        if (nullable && value != null && value.isNull()) { return null; }
        throw failure("CONFIG_LKG_INVALID", false);
    }

    private static Instant instant(JsonNode node, String name, boolean nullable) {
        String value = text(node, name, nullable);
        if (value == null) { return null; }
        if (!value.endsWith("Z")) { throw failure("CONFIG_LKG_INVALID", false); }
        return Instant.parse(value);
    }

    private static long number(String value, boolean zero) {
        if (value == null || !value.matches(zero ? "0|[1-9][0-9]{0,18}" : "[1-9][0-9]{0,18}")) {
            throw failure("CONFIG_LKG_INVALID", false);
        }
        try { return Long.parseLong(value); }
        catch (NumberFormatException invalid) { throw failure("CONFIG_LKG_INVALID", false); }
    }

    private static String token(String value, int maximum) {
        if (value == null || value.isEmpty() || value.length() > maximum
                || !value.chars().allMatch(character -> character >= 0x21 && character <= 0x7e)) {
            throw failure("CONFIG_LKG_INVALID", false);
        }
        return value;
    }

    private static String digest(String value) {
        if (value == null || !value.matches("sha256:[0-9a-f]{64}")) { throw failure("CONFIG_LKG_INVALID", false); }
        return value;
    }

    private static JsonNode pending(JsonNode value, IncarnationVO reportIncarnation, long lastReportSequence,
            String restartDigest, DdcConfigSnapshotDTO applied) {
        fields(value, PENDING_FIELDS);
        ObjectNode body = ((ObjectNode) value).deepCopy();
        String errorCode = text(body, "errorCode", true);
        String errorMessage = text(body, "errorMessage", true);
        body.put("errorCode", errorCode == null || errorCode.isBlank() ? null : errorCode);
        body.put("errorMessage", errorMessage == null || errorMessage.isBlank() ? null : errorMessage);
        var command = DdcConfigReceiptCommand.builder().schemaVersion(text(body, "schemaVersion", false))
                .bootId(text(body, "bootId", false)).releaseRevision(text(body, "releaseRevision", false))
                .reportSeq(text(body, "reportSeq", false)).appliedRevision(text(body, "appliedRevision", true))
                .contentSha256(text(body, "contentSha256", false)).appliedContentSha256(text(body, "appliedContentSha256", true))
                .baselineRestartDigest(text(body, "baselineRestartDigest", false)).receiptDigest(text(body, "receiptDigest", false))
                .status(DdcConfigReceiptStatusEnum.fromCode(text(body, "status", false)))
                .reportedAt(instant(body, "reportedAt", false)).appliedAt(instant(body, "appliedAt", true))
                .errorCode(text(body, "errorCode", true)).errorMessage(text(body, "errorMessage", true)).build();
        if (!"1".equals(command.getSchemaVersion()) || reportIncarnation == null
                || !reportIncarnation.bootId().equals(command.getBootId())
                || number(command.getReportSeq(), false) != lastReportSequence
                || !restartDigest.equals(command.getBaselineRestartDigest()) || !command.isReceiptShapeValid()
                || !DdcConfigCanonicalJson.receiptDigest(command).equals(command.getReceiptDigest())
                || command.getStatus().isSuccessful() && applied == null
                || applied != null && (!Objects.equals(applied.getReleaseRevision(), command.getAppliedRevision())
                    || !Objects.equals(applied.getContentSha256(), command.getAppliedContentSha256()))) {
            throw failure("CONFIG_LKG_PENDING_INVALID", false);
        }
        number(command.getReleaseRevision(), false); digest(command.getContentSha256()); digest(command.getReceiptDigest());
        // A failed/restart-required report can describe an in-memory baseline without claiming recoverable LKG content.
        if (command.getAppliedRevision() != null) {
            number(command.getAppliedRevision(), false);
            digest(command.getAppliedContentSha256());
        }
        if (command.getErrorCode() != null && !command.getErrorCode().matches("[A-Z][A-Z0-9_]{0,63}")) { throw failure("CONFIG_LKG_PENDING_INVALID", false); }
        if (command.getErrorMessage() != null && command.getErrorMessage().length() > 1024) { throw failure("CONFIG_LKG_PENDING_INVALID", false); }
        return body;
    }

    public record ScopeKeyVO(@Valid @NotNull DdcConfigScopeVO scope, String configId, String bindingId, String clusterId,
            String kubernetesNamespace, String workloadUid, String containerName) {
        public ScopeKeyVO {
            Objects.requireNonNull(scope, "scope"); number(configId, false); token(bindingId, 128); token(clusterId, 64);
            token(workloadUid, 64); token(kubernetesNamespace, 63); token(containerName, 63);
        }
        @Override public String toString() { return "LKG scope key"; }
    }

    public record IncarnationVO(String podUid, String containerId, String bootId) {
        public IncarnationVO {
            token(podUid, 64); token(containerId, 256);
            if (bootId == null || !bootId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) { throw failure("CONFIG_LKG_INVALID", false); }
        }
        @Override public String toString() { return "LKG incarnation"; }
    }

    public record StateVO(@Valid @NotNull ScopeKeyVO key, long bindingRevision, String restartDigest, String podUid,
            @Nullable String disabledRevision, boolean isolated, @Valid @Nullable DdcConfigSnapshotDTO applied,
            @Nullable Instant lastVerifiedAt, @Valid @Nullable IncarnationVO reportIncarnation, long lastReportSequence,
            @Nullable JsonNode pendingReceipt) {
        public StateVO {
            Objects.requireNonNull(key, "key"); digest(restartDigest); token(podUid, 64);
            if (bindingRevision <= 0 || lastReportSequence < 0) { throw failure("CONFIG_LKG_INVALID", false); }
            if (disabledRevision != null) { number(disabledRevision, false); }
            if (applied != null && (!key.scope().equals(applied.getScope()) || !restartDigest.equals(applied.getRestartDigest())
                    || !DdcConfigCanonicalJson.contentSha256(applied).equals(applied.getContentSha256()) || lastVerifiedAt == null)) {
                throw failure("CONFIG_LKG_APPLIED_INVALID", false);
            }
            if (reportIncarnation == null && (lastReportSequence != 0 || pendingReceipt != null)
                    || reportIncarnation != null && !podUid.equals(reportIncarnation.podUid())) { throw failure("CONFIG_LKG_PENDING_INVALID", false); }
            if (pendingReceipt != null) {
                pendingReceipt = pending(pendingReceipt, reportIncarnation, lastReportSequence, restartDigest, applied);
            }
        }
        @Override public JsonNode pendingReceipt() { return pendingReceipt == null ? null : pendingReceipt.deepCopy(); }
        @Override public String toString() { return "LKG state"; }
    }

    /** Sanitized stable failure; post-replace uncertainty must latch readiness closed until reconciliation. */
    public static final class LkgStoreException extends IllegalStateException {
        private final String code;
        private final boolean indeterminate;
        private LkgStoreException(String code, boolean indeterminate) { super(code); this.code = code; this.indeterminate = indeterminate; }
        public String code() { return code; }
        public boolean durabilityIndeterminate() { return indeterminate; }
    }

    private static LkgStoreException failure(String code, boolean indeterminate) { return new LkgStoreException(code, indeterminate); }
}
