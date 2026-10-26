package top.egon.cola.component.tianshu.admin.service.i18n;

import top.egon.cola.component.tianshu.admin.model.bo.DdcSourceImportIdentityBO;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CanonicalJson;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.DefinitionLifecycle;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifestCanonicalizer;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionMode;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionRequirement;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceAccess;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.SourceImportRequest;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceIdentityPrincipal;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DdcSourceImportTestFixture {
    public static final CatalogScope SCOPE = new CatalogScope("501", "101", "trade", "order", "main", "test");
    public static final URI ISSUER = URI.create("https://identity.example.test/tenants/101");
    public static final URI RESOURCE = URI.create("https://metadata.example.test");
    public static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");
    private static final PermissionManifestCanonicalizer CANONICAL = new PermissionManifestCanonicalizer();

    private DdcSourceImportTestFixture() { }

    public static DdcSourceImportIdentityBO identity() {
        return new DdcSourceImportIdentityBO(ISSUER, principal(ServiceTokenContext.TENANT, Set.of(DdcSourceImportIdentityBO.REQUIRED_SCOPE)));
    }

    public static ServiceIdentityPrincipal principal(ServiceTokenContext context, Set<String> scopes) {
        return new ServiceIdentityPrincipal("rbac-publisher", context == ServiceTokenContext.TENANT ? "101" : null,
                "rbac-publisher", "token-1", RESOURCE, 1, scopes, "platform", "rbac", "test", "credential-1",
                NOW, NOW.plusSeconds(300), "rbac-app", context);
    }

    public static SourceImportRequest request() {
        List<PermissionManifest.PermissionDefinition> permissions = new ArrayList<>();
        List<PermissionManifest.Message> messages = new ArrayList<>();
        for (String code : List.of("order:read", "order:write", "order:field:read")) {
            String key = code.replace(':', '.');
            permissions.add(new PermissionManifest.PermissionDefinition(code, code, new PermissionManifest.I18nReference("common", key), DefinitionLifecycle.ACTIVE));
            messages.add(new PermissionManifest.Message("common", key, "zh-CN", Map.of("zh-CN", "权限 " + code, "en-US", "Permission " + code)));
        }
        List<PermissionManifest.ResourceDefinition> resources = new ArrayList<>();
        PermissionRequirement all = new PermissionRequirement(PermissionMode.ALL, List.of("order:read"));
        for (ResourceType type : ResourceType.values()) {
            String key = "order." + type.name().toLowerCase(java.util.Locale.ROOT);
            PermissionManifest.FieldPolicy field = type == ResourceType.FIELD ? new PermissionManifest.FieldPolicy(PermissionManifest.JsonFieldType.STRING,
                    new PermissionRequirement(PermissionMode.ALL, List.of("order:field:read")), all,
                    new PermissionRequirement(PermissionMode.ALL, List.of("order:write")), "FIXED") : null;
            Map<String, Object> target = type == ResourceType.API ? Map.of("methods", List.of("GET", "POST"), "path", "/orders") : Map.of("key", key);
            resources.add(new PermissionManifest.ResourceDefinition(key, type, target, ResourceAccess.PROTECTED, all, field,
                    List.of(PermissionManifest.PrincipalType.USER), List.of("ORDER_API"), new PermissionManifest.I18nReference("orders", key), DefinitionLifecycle.ACTIVE));
            messages.add(new PermissionManifest.Message("orders", key, "zh-CN", Map.of("zh-CN", "资源 {{name}}", "en-US", "Resource {{name}}")));
        }
        PermissionManifest manifest = new PermissionManifest("1.0", new PermissionManifest.Application("trade", "order"),
                new PermissionManifest.Producer("order-admin", PermissionManifest.ProducerKind.JAVA, PermissionManifest.SnapshotMode.FULL,
                        List.of("order."), List.of("admin"), List.of("admin"), false),
                new PermissionManifest.Artifact("order-service", "1".repeat(40), "build-1", "0.1.0"),
                permissions, List.of(), resources, List.of(), messages);
        Map<String, String> resourceIds = new LinkedHashMap<>();
        for (int index = 0; index < resources.size(); index++) resourceIds.put(resources.get(index).key(), Long.toString(9_007_199_254_740_993L + index));
        Map<String, String> permissionIds = Map.of("order:read", "601", "order:write", "602", "order:field:read", "603");
        var digests = CANONICAL.digests(manifest);
        return new SourceImportRequest("32fb3f12-7ce5-4e19-8665-a5db0d9c1baa", SCOPE, "order-admin", "1", digests.manifestDigest(), manifest, digests, resourceIds, permissionIds);
    }

    public static SourceImportRequest next(SourceImportRequest request) {
        return new SourceImportRequest(UUID.randomUUID().toString(), request.scope(), request.producerId(), "2", request.sourceVersion(),
                request.manifest(), request.digests(), request.resourceIds(), request.permissionIds());
    }

    public static Map<String, Object> body(SourceImportRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("operationId", request.operationId());
        body.put("scope", new LinkedHashMap<>(request.scopeMap()));
        body.put("producerId", request.producerId());
        body.put("producerVersion", request.producerVersion());
        body.put("sourceVersion", request.sourceVersion());
        body.put("manifest", CANONICAL.toMap(request.manifest()));
        body.put("digests", Map.of("policyDigest", request.digests().policyDigest(), "metadataDigest", request.digests().metadataDigest(), "manifestDigest", request.digests().manifestDigest()));
        body.put("resourceIds", new LinkedHashMap<>(request.resourceIds()));
        body.put("permissionIds", new LinkedHashMap<>(request.permissionIds()));
        return body;
    }

    public static byte[] bytes(Map<String, Object> body) { return CanonicalJson.write(body).getBytes(StandardCharsets.UTF_8); }
}
