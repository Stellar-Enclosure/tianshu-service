package top.egon.cola.component.tianshu.admin.service.i18n;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CanonicalJson;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ManifestDigests;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifestReader;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.SourceImportReceipt;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.SourceImportRequest;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Bounded JSON boundary; the shared contract remains the only manifest parser and digest definition. */
@Component("ddcI18nSourceImportJson")
public class DdcI18nSourceImportJson {
    public static final int MAX_BODY_BYTES = PermissionManifest.MAX_BYTES + 4 * 1024 * 1024 + 64 * 1024;
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() { };
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(18)
                    .maxStringLength(2 * 1024 * 1024).maxNumberLength(32).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final PermissionManifestReader manifests = new PermissionManifestReader();

    public SourceImportRequest read(byte[] body) {
        if (body == null || body.length == 0) throw new IllegalArgumentException("Source import JSON is required");
        if (body.length > MAX_BODY_BYTES) throw new BodyTooLargeException();
        Map<String, Object> request = object(parse(body), "operationId", "scope", "producerId", "producerVersion",
                "sourceVersion", "manifest", "digests", "resourceIds", "permissionIds");
        return new SourceImportRequest(text(request, "operationId"), scope(request.get("scope")), text(request, "producerId"),
                text(request, "producerVersion"), text(request, "sourceVersion"),
                manifests.read(objectMap(request.get("manifest"))), digests(request.get("digests")),
                ids(request.get("resourceIds")), ids(request.get("permissionIds")));
    }

    public String writeReceipt(SourceImportReceipt receipt) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("receiptId", receipt.receiptId());
        result.put("operationId", receipt.operationId());
        result.put("scope", scopeMap(receipt.scope()));
        result.put("producerId", receipt.producerId());
        result.put("producerVersion", receipt.producerVersion());
        result.put("sourceVersion", receipt.sourceVersion());
        result.put("digests", Map.of("policyDigest", receipt.digests().policyDigest(),
                "metadataDigest", receipt.digests().metadataDigest(), "manifestDigest", receipt.digests().manifestDigest()));
        result.put("projectionDigest", receipt.projectionDigest());
        result.put("resourceCount", receipt.resourceCount());
        result.put("permissionCount", receipt.permissionCount());
        result.put("messageCount", receipt.messageCount());
        result.put("importedAt", receipt.importedAt().toString());
        return CanonicalJson.write(result);
    }

    public SourceImportReceipt readReceipt(String stored) {
        if (stored == null || stored.getBytes(StandardCharsets.UTF_8).length > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("Stored source import receipt is invalid");
        }
        Map<String, Object> receipt = object(parse(stored.getBytes(StandardCharsets.UTF_8)), "receiptId", "operationId", "scope",
                "producerId", "producerVersion", "sourceVersion", "digests", "projectionDigest", "resourceCount",
                "permissionCount", "messageCount", "importedAt");
        return new SourceImportReceipt(text(receipt, "receiptId"), text(receipt, "operationId"), scope(receipt.get("scope")),
                text(receipt, "producerId"), text(receipt, "producerVersion"), text(receipt, "sourceVersion"),
                digests(receipt.get("digests")), text(receipt, "projectionDigest"), count(receipt, "resourceCount"),
                count(receipt, "permissionCount"), count(receipt, "messageCount"), Instant.parse(text(receipt, "importedAt")));
    }

    public CatalogScope scope(Object value) {
        Map<String, Object> scope = object(value, "catalogScopeId", "tenantId", "businessCode", "applicationCode", "namespaceCode", "environment");
        return new CatalogScope(text(scope, "catalogScopeId"), text(scope, "tenantId"), text(scope, "businessCode"),
                text(scope, "applicationCode"), text(scope, "namespaceCode"), text(scope, "environment"));
    }

    public Map<String, Object> scopeMap(CatalogScope scope) {
        return Map.of("catalogScopeId", scope.catalogScopeId(), "tenantId", scope.tenantId(), "businessCode", scope.businessCode(),
                "applicationCode", scope.applicationCode(), "namespaceCode", scope.namespaceCode(), "environment", scope.environment());
    }

    private ManifestDigests digests(Object value) {
        Map<String, Object> digests = object(value, "policyDigest", "metadataDigest", "manifestDigest");
        return new ManifestDigests(text(digests, "policyDigest"), text(digests, "metadataDigest"), text(digests, "manifestDigest"));
    }

    private Map<String, Object> parse(byte[] body) {
        try {
            return mapper.readValue(body, OBJECT);
        } catch (JsonProcessingException invalid) {
            throw new IllegalArgumentException("Invalid source import JSON", invalid);
        } catch (java.io.IOException failure) {
            throw new IllegalArgumentException("Unreadable source import JSON", failure);
        }
    }

    private Map<String, Object> object(Object value, String... names) {
        Map<String, Object> object = objectMap(value);
        Set<String> allowed = Set.of(names);
        if (!allowed.equals(object.keySet())) throw new IllegalArgumentException("Source import contains missing or unknown properties");
        return object;
    }

    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> input)) throw new IllegalArgumentException("A JSON object is required");
        Map<String, Object> result = new LinkedHashMap<>();
        input.forEach((key, item) -> {
            if (!(key instanceof String name)) throw new IllegalArgumentException("JSON keys must be strings");
            result.put(name, item);
        });
        return result;
    }

    private Map<String, String> ids(Object value) {
        Map<String, Object> object = objectMap(value);
        if (object.size() > 10_000) throw new IllegalArgumentException("Centre ID map exceeds the contract limit");
        Map<String, String> result = new LinkedHashMap<>();
        object.forEach((key, id) -> {
            if (!(id instanceof String text)) throw new IllegalArgumentException("Centre IDs must be decimal strings");
            result.put(key, text);
        });
        return result;
    }

    private String text(Map<String, Object> object, String key) {
        if (!(object.get(key) instanceof String value)) throw new IllegalArgumentException(key + " must be a string");
        return value;
    }

    private int count(Map<String, Object> object, String key) {
        if (!(object.get(key) instanceof Integer value)) throw new IllegalArgumentException(key + " must be an integer");
        return value;
    }

    public static final class BodyTooLargeException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public BodyTooLargeException() { super("Source import body exceeds its byte limit"); }
    }
}
