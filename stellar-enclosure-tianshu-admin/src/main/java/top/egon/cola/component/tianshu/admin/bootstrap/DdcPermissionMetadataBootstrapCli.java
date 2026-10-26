package top.egon.cola.component.tianshu.admin.bootstrap;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CanonicalJson;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Operator-only one-shot B0 command. It never creates a Spring context, starts providers, or alters a migration. */
public final class DdcPermissionMetadataBootstrapCli {
    private static final int MAX_BYTES = 1024 * 1024;
    private static final ObjectMapper JSON = com.fasterxml.jackson.databind.json.JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16).maxStringLength(MAX_BYTES).build()).build())
            .disable(com.fasterxml.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() { };
    static {
        var text = JSON.coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Textual);
        for (var shape : new com.fasterxml.jackson.databind.cfg.CoercionInputShape[]{com.fasterxml.jackson.databind.cfg.CoercionInputShape.Integer,
                com.fasterxml.jackson.databind.cfg.CoercionInputShape.Float, com.fasterxml.jackson.databind.cfg.CoercionInputShape.Boolean}) {
            text.setCoercion(shape, com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
        }
    }


    private DdcPermissionMetadataBootstrapCli() { }

    public static void run(String[] arguments) {
        try {
            if ("true".equalsIgnoreCase(System.getenv("CI"))) throw new IllegalArgumentException("ordinary CI cannot initialize metadata grants");
            Map<String, Path> paths = arguments(arguments);
            byte[] input = privateBytes(paths.get("--command-file"), MAX_BYTES);
            var command = readCommand(input);
            var database = JSON.readValue(privateBytes(Path.of(command.connectionFile()), MAX_BYTES), Database.class);
            String digest = CanonicalJson.digest(Map.of("command", JSON.readValue(input, OBJECT), "database", database.identity()));
            Path output = validateOutput(paths.get("--receipt-file"));
            String expectedReceipt = null;
            if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
                expectedReceipt = new String(privateBytes(output, MAX_BYTES), StandardCharsets.UTF_8).stripTrailing();
                var previous = object(expectedReceipt);
                if (!digest.equals(previous.get("commandDigest")) || !command.operationId().equals(previous.get("operationId"))) {
                    throw new IllegalArgumentException("receipt belongs to another command");
                }
            }
            byte[] secret = privateBytes(Path.of(database.passwordFile()), 16 * 1024);
            if (secret.length == 0) throw new IllegalArgumentException("empty database credential");
            Properties credentials = new Properties();
            credentials.setProperty("user", database.username());
            credentials.setProperty("password", new String(secret, StandardCharsets.UTF_8));
            credentials.setProperty("connectTimeout", "10"); credentials.setProperty("socketTimeout", "20");
            java.util.Arrays.fill(secret, (byte) 0);
            String receipt;
            try (var connection = DriverManager.getConnection(database.jdbcUrl(), credentials)) {
                try (var statement = connection.prepareStatement("SELECT datname, oid::text, pg_catalog.pg_get_userbyid(datdba), current_user FROM pg_catalog.pg_database WHERE datname=pg_catalog.current_database()")) {
                    try (var rows = statement.executeQuery()) {
                        if (!rows.next() || !database.databaseName().equals(rows.getString(1)) || !database.databaseOid().equals(rows.getString(2))
                                || !database.databaseOwner().equals(rows.getString(3)) || !database.username().equals(rows.getString(4))) {
                            throw new IllegalArgumentException("database identity mismatch");
                        }
                    }
                }
                receipt = new DdcPermissionMetadataBootstrapRepository(database.schema(), Clock.systemUTC()).initialize(connection, command, digest, expectedReceipt);
            } finally { credentials.clear(); }
            writeReceipt(output, receipt);
        } catch (IOException | SQLException | RuntimeException failure) {
            // Driver/JSON errors can contain credentials or command bodies. Recovery uses the same operationId and durable SQL receipt.
            throw new IllegalStateException("Metadata B0 did not produce a confirmed receipt; verify private inputs and database readiness, then resume the same operationId");
        }
    }

    static DdcPermissionMetadataBootstrapCommand readCommand(byte[] bytes) throws IOException {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) DdcPermissionMetadataBootstrapCommand.invalid();
        return JSON.readValue(bytes, DdcPermissionMetadataBootstrapCommand.class);
    }

    static Map<String, Object> object(String value) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) DdcPermissionMetadataBootstrapCommand.invalid();
        try { return JSON.readValue(value, OBJECT); }
        catch (IOException invalid) { throw new IllegalArgumentException("METADATA_BOOTSTRAP_RECEIPT_INVALID"); }
    }

    static Map<String, Path> arguments(String[] args) {
        if (args.length != 4) DdcPermissionMetadataBootstrapCommand.invalid();
        Map<String, Path> values = new HashMap<>();
        for (int index = 0; index < args.length; index += 2) {
            if (!Set.of("--command-file", "--receipt-file").contains(args[index])) DdcPermissionMetadataBootstrapCommand.invalid();
            Path path = Path.of(args[index + 1]);
            if (!path.isAbsolute() || values.putIfAbsent(args[index], path.normalize()) != null) DdcPermissionMetadataBootstrapCommand.invalid();
        }
        if (values.get("--command-file").equals(values.get("--receipt-file"))) DdcPermissionMetadataBootstrapCommand.invalid();
        return values;
    }

    static byte[] privateBytes(Path path, int maximum) throws IOException {
        if (!path.isAbsolute() || path.getParent() == null) throw new IOException("absolute private file required");
        path = trustedDirectory(path.getParent()).resolve(path.getFileName());
        if (!path.isAbsolute() || Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.size(path) > maximum) throw new IOException("private bounded file required");
        var attributes = Files.readAttributes(path, java.nio.file.attribute.PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        var owner = path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
        if (!attributes.owner().equals(owner) || attributes.permissions().stream().anyMatch(permission -> permission.name().startsWith("GROUP_")
                || permission.name().startsWith("OTHERS_")) || !attributes.permissions().contains(PosixFilePermission.OWNER_READ)) {
            throw new IOException("owner-only file required");
        }
        byte[] bytes;
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) { bytes = input.readNBytes(maximum + 1); }
        var after = Files.readAttributes(path, java.nio.file.attribute.PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (bytes.length > maximum || !java.util.Objects.equals(attributes.fileKey(), after.fileKey())
                || attributes.size() != after.size() || !attributes.lastModifiedTime().equals(after.lastModifiedTime())
                || !attributes.owner().equals(after.owner()) || !attributes.permissions().equals(after.permissions())) {
            throw new IOException("private file changed while reading");
        }
        return bytes;
    }

    private static Path trustedDirectory(Path directory) throws IOException {
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("owner-controlled directory required");
        }
        Path resolved = directory.toRealPath();
        var owner = resolved.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
        directoryLineage(directory, owner);
        directoryLineage(resolved, owner);
        // Reads/writes use this resolved path, so a mutable ancestor alias cannot redirect the opened file.
        return resolved;
    }

    private static void directoryLineage(Path directory, java.nio.file.attribute.UserPrincipal owner) throws IOException {
        for (Path ancestor = directory; ancestor != null; ancestor = ancestor.getParent()) {
            var attributes = Files.readAttributes(ancestor, java.nio.file.attribute.PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            boolean rootOwned = ((Number) Files.getAttribute(ancestor, "unix:uid", LinkOption.NOFOLLOW_LINKS)).longValue() == 0;
            boolean symbolicAncestor = attributes.isSymbolicLink() && !ancestor.equals(directory);
            boolean writableByOthers = !symbolicAncestor && (attributes.permissions().contains(PosixFilePermission.GROUP_WRITE)
                    || attributes.permissions().contains(PosixFilePermission.OTHERS_WRITE));
            boolean rootStickyAncestor = !ancestor.equals(directory) && rootOwned && !symbolicAncestor
                    && (((Number) Files.getAttribute(ancestor, "unix:mode", LinkOption.NOFOLLOW_LINKS)).intValue() & 01000) != 0;
            if (!attributes.isDirectory() && !symbolicAncestor || !attributes.owner().equals(owner) && !rootOwned
                    || writableByOthers && !rootStickyAncestor || ancestor.equals(directory) && !attributes.owner().equals(owner)) {
                throw new IOException("private file directory ancestry is not owner controlled");
            }
        }
    }

    private static Path validateOutput(Path output) throws IOException {
        if (!output.isAbsolute() || output.getParent() == null || Files.isSymbolicLink(output)) throw new IOException("private receipt directory required");
        return trustedDirectory(output.getParent()).resolve(output.getFileName());
    }

    static void writeReceipt(Path output, String receipt) throws IOException {
        output = validateOutput(output);
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
            String existing = new String(privateBytes(output, MAX_BYTES), StandardCharsets.UTF_8).stripTrailing();
            if (!existing.equals(receipt)) throw new IOException("receipt output differs from original database receipt");
            return;
        }
        Path temporary = Files.createTempFile(output.getParent(), ".metadata-bootstrap-", ".tmp", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try {
            Files.writeString(temporary, receipt + "\n", StandardCharsets.UTF_8);
            // A hard link publishes the complete private file atomically and cannot overwrite another operation's receipt.
            Files.createLink(output, temporary);
        } finally { Files.deleteIfExists(temporary); }
    }

    record Database(int schemaVersion, String logicalDataSource, String jdbcUrl, String username, String passwordFile,
                    String schema, String databaseName, String databaseOid, String databaseOwner) {
        Database {
            if (schemaVersion != 1 || !"tianshu_0".equals(logicalDataSource) || jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql://")) {
                DdcPermissionMetadataBootstrapCommand.invalid();
            }
            URI uri = URI.create(jdbcUrl.substring(5));
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null || jdbcUrl.length() > 4096
                    || uri.getRawPath() == null || !uri.getRawPath().matches("/[A-Za-z0-9_-]+")) DdcPermissionMetadataBootstrapCommand.invalid();
            if (uri.getRawQuery() != null) {
                for (String pair : uri.getRawQuery().split("&", -1)) {
                    if (!Set.of("sslmode", "sslrootcert", "connectTimeout", "socketTimeout").contains(pair.split("=", 2)[0])) DdcPermissionMetadataBootstrapCommand.invalid();
                }
            }
            DdcPermissionMetadataBootstrapCommand.identifier(username, 128);
            DdcPermissionMetadataBootstrapCommand.identifier(databaseName, 128);
            DdcPermissionMetadataBootstrapCommand.identifier(databaseOwner, 128);
            DdcPermissionMetadataBootstrapCommand.id(databaseOid);
            if (!uri.getRawPath().equals("/" + databaseName) || !"public".equals(schema)
                    || passwordFile == null || !Path.of(passwordFile).isAbsolute()) DdcPermissionMetadataBootstrapCommand.invalid();
        }
        Map<String, Object> identity() {
            return Map.of("logicalDataSource", logicalDataSource, "jdbcUrl", jdbcUrl, "username", username,
                    "schema", schema, "databaseName", databaseName, "databaseOid", databaseOid, "databaseOwner", databaseOwner);
        }
    }
}
