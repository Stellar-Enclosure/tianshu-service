package top.egon.cola.component.tianshu.admin.service.i18n;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import top.egon.cola.component.common.core.enums.ResultCode;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ActivationDescriptor;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ManifestDigests;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict wire boundary; validation and canonical digests belong to the shared RBAC contract. */
@Component("ddcCatalogActivationJson")
public class DdcCatalogActivationJson {
    public static final int MAX_RESPONSE_BYTES = PermissionManifest.MAX_BYTES + 64 * 1024;
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(18).maxStringLength(8192).maxNumberLength(32).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public ActivationDescriptor readResponse(byte[] bytes) {
        Map<String, Object> envelope = fields(parse(bytes), "success", "code", "status", "message", "data", "traceId", "timestamp");
        if (!Boolean.TRUE.equals(envelope.get("success")) || !(envelope.get("code") instanceof Integer code)
                || code != ResultCode.SUCCESS.getCode()) throw new IllegalArgumentException("Catalog response is unsuccessful");
        return descriptor(envelope.get("data"));
    }

    public ActivationDescriptor read(String value) {
        return descriptor(parse(value.getBytes(StandardCharsets.UTF_8)));
    }

    private ActivationDescriptor descriptor(Object value) {
        var data = fields(value, "scope", "activationVersion", "operationId", "policyVersion", "createdAt", "producers");
        var scope = fields(data.get("scope"), "catalogScopeId", "tenantId", "businessCode", "applicationCode", "namespaceCode", "environment");
        if (!(data.get("producers") instanceof List<?> rows) || rows.size() > 256) throw new IllegalArgumentException("Invalid activation producers");
        List<ActivationDescriptor.ProducerVersion> producers = new ArrayList<>();
        for (Object row : rows) {
            var producer = fields(row, "producerId", "producerVersion", "sourceVersion", "digests", "qualificationId", "artifactDigest");
            var hashes = fields(producer.get("digests"), "policyDigest", "metadataDigest", "manifestDigest");
            producers.add(new ActivationDescriptor.ProducerVersion(text(producer, "producerId"), text(producer, "producerVersion"),
                    text(producer, "sourceVersion"), new ManifestDigests(text(hashes, "policyDigest"), text(hashes, "metadataDigest"),
                    text(hashes, "manifestDigest")), text(producer, "qualificationId"), text(producer, "artifactDigest")));
        }
        ActivationDescriptor result = new ActivationDescriptor(new CatalogScope(text(scope, "catalogScopeId"), text(scope, "tenantId"),
                text(scope, "businessCode"), text(scope, "applicationCode"), text(scope, "namespaceCode"), text(scope, "environment")),
                text(data, "activationVersion"), text(data, "operationId"), text(data, "policyVersion"), Instant.parse(text(data, "createdAt")), producers);
        result.canonicalize();
        return result;
    }

    private Map<String, Object> parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_RESPONSE_BYTES) throw new IllegalArgumentException("Catalog JSON exceeds its byte limit");
        try { return mapper.readValue(bytes, new TypeReference<LinkedHashMap<String, Object>>() { }); }
        catch (IOException failure) { throw new IllegalArgumentException("Invalid catalog JSON", failure); }
    }

    private Map<String, Object> fields(Object value, String... expected) {
        if (!(value instanceof Map<?, ?> map) || !Set.of(expected).equals(map.keySet())) throw new IllegalArgumentException("Missing or unknown catalog properties");
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put((String) key, item));
        return result;
    }

    private String text(Map<String, Object> map, String key) {
        if (!(map.get(key) instanceof String value)) throw new IllegalArgumentException("Catalog identifiers must be strings");
        return value;
    }
}
