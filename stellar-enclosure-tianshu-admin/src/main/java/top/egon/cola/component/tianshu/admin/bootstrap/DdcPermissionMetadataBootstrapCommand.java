package top.egon.cola.component.tianshu.admin.bootstrap;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.CanonicalJson;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Explicit owner input for clean-environment metadata targets; no role, manifest or activation data is accepted. */
public record DdcPermissionMetadataBootstrapCommand(int schemaVersion, String operationId, CatalogScope target,
        String connectionFile, String actorId, String ddcBusinessId, String ddcApplicationId,
        RowIds ids, List<SourceGrant> sourceImportGrants) {
    public DdcPermissionMetadataBootstrapCommand {
        if (schemaVersion != 1 || operationId == null || !UUID.fromString(operationId).toString().equals(operationId)) invalid();
        Objects.requireNonNull(target, "target");
        if (connectionFile == null || !java.nio.file.Path.of(connectionFile).isAbsolute()) invalid();
        identifier(actorId, 128); identifier(ddcBusinessId, 64); identifier(ddcApplicationId, 64);
        Objects.requireNonNull(ids, "ids");
        if (sourceImportGrants == null || sourceImportGrants.isEmpty() || sourceImportGrants.size() > 256) invalid();
        sourceImportGrants = List.copyOf(sourceImportGrants);
        var keys = new HashSet<String>();
        var grantIds = new HashSet<Long>();
        for (SourceGrant grant : sourceImportGrants) {
            if (!keys.add(grant.producerId() + ":" + grant.bindingDigest()) || !grantIds.add(id(grant.id()))) invalid();
        }
    }

    public record RowIds(String business, String environment, String application, String namespace,
                         String binding, String metadataScope, String audit) {
        public RowIds {
            for (String value : List.of(business, environment, application, namespace, binding, metadataScope, audit)) id(value);
        }
    }

    public record SourceGrant(String id, String producerId, String issuer, String clientId, String resourceUri) {
        public SourceGrant {
            DdcPermissionMetadataBootstrapCommand.id(id); identifier(producerId, 128); identifier(clientId, 200);
            https(issuer); https(resourceUri);
        }
        public String bindingDigest() {
            return CanonicalJson.digest(Map.of("issuer", issuer, "clientId", clientId, "resourceUri", resourceUri));
        }
    }

    static long id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) invalid();
        long result = Long.parseLong(value);
        if (result <= 0) invalid();
        return result;
    }

    static void identifier(String value, int max) {
        if (value == null || value.length() > max || !value.matches("[A-Za-z0-9][A-Za-z0-9._:/@-]*")) invalid();
    }

    static void https(String value) {
        URI uri = URI.create(Objects.requireNonNull(value, "URI"));
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getRawQuery() != null || uri.getFragment() != null || !uri.equals(uri.normalize()) || value.length() > 2048) invalid();
    }

    static void invalid() { throw new IllegalArgumentException("METADATA_BOOTSTRAP_INPUT_INVALID"); }
}
