package top.egon.cola.component.tianshu.admin.service.i18n;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nSourceMessageBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nOverrideEntryBO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcI18nBundleResult;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ActivationDescriptor;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CanonicalJson;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.I18nSourceRules;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Per-key selection keeps source ownership, human publication and locale fallback independent. */
@Component("ddcI18nBundleAssembler")
public class DdcI18nBundleAssembler {
    public static final int MAX_RESPONSE_BYTES = PermissionManifest.MAX_BYTES;
    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, PermissionManifest.Message> sources(ActivationDescriptor descriptor, String pageCode, List<DdcI18nSourceMessageBO> rows) {
        I18nSourceRules.key(pageCode);
        Map<String, PermissionManifest.Message> result = new TreeMap<>();
        for (var row : rows) {
            if (!pageCode.equals(row.getPageCode())) continue;
            var message = source(descriptor, row);
            if (result.putIfAbsent(message.key(), message) != null) throw corrupt();
        }
        if (result.isEmpty()) throw DdcCatalogActivationClient.missing();
        String defaultLocale = result.values().iterator().next().defaultLocale();
        if (result.values().stream().anyMatch(source -> !source.defaultLocale().equals(defaultLocale))) throw corrupt();
        return Collections.unmodifiableMap(result);
    }

    public DdcI18nBundleResult assemble(ActivationDescriptor descriptor, String pageCode, String locale,
            List<DdcI18nSourceMessageBO> rows, List<DdcI18nOverrideEntryBO> overrides, Map<String, String> publishedRevisions) {
        I18nSourceRules.key(pageCode); I18nSourceRules.locale(locale);
        Map<String, PermissionManifest.Message> page = new TreeMap<>();
        Map<String, PermissionManifest.Message> common = new TreeMap<>();
        Map<String, String> hashes = new HashMap<>();
        Map<String, String> sourceVersions = new TreeMap<>();
        descriptor.producers().forEach(producer -> sourceVersions.put(producer.producerId(), producer.sourceVersion()));
        for (var row : rows) {
            if (!row.getPageCode().equals(pageCode) && !row.getPageCode().equals("common")) throw corrupt();
            var message = source(descriptor, row);
            var target = row.getPageCode().equals(pageCode) ? page : common;
            if (target.putIfAbsent(message.key(), message) != null) throw corrupt();
            hashes.put(identity(message.pageCode(), message.key()), row.getSourceHash());
        }
        if (page.isEmpty()) throw DdcCatalogActivationClient.missing();
        String defaultLocale = page.values().iterator().next().defaultLocale();
        if (page.values().stream().anyMatch(message -> !defaultLocale.equals(message.defaultLocale()))) throw corrupt();
        Map<String, DdcI18nOverrideEntryBO> current = new HashMap<>();
        Map<String, DdcI18nOverrideEntryBO> matching = new HashMap<>();
        for (var row : overrides) {
            var target = "CURRENT".equals(row.getSelectionKind()) ? current : "MATCHING".equals(row.getSelectionKind()) ? matching : null;
            if (target == null || row.getPublicationRevision() == null || row.getPublicationRevision() <= 0
                    || target.putIfAbsent(identity(row.getPageCode(), row.getMessageKey()), row) != null) throw corrupt();
        }
        Map<String, PermissionManifest.Message> selected = new TreeMap<>(common);
        selected.putAll(page);
        Map<String, String> messages = new TreeMap<>();
        List<DdcI18nBundleResult.Diagnostic> diagnostics = new ArrayList<>();
        for (var message : selected.values()) {
            String key = identity(message.pageCode(), message.key());
            Map<String, String> human = compatible(message, hashes.get(key), current.get(key), matching.get(key), diagnostics);
            String value = human.get(locale);
            if (value == null) value = message.defaults().get(locale);
            if (value == null) value = human.get(message.defaultLocale());
            if (value == null) value = message.defaults().get(message.defaultLocale());
            messages.put(message.key(), value == null ? message.key() : value);
        }
        var revisionCopy = Collections.unmodifiableMap(new TreeMap<>(publishedRevisions));
        String version = CanonicalJson.digest(Map.of("contract", "tianshu-source-bundle-v1", "descriptorDigest", descriptor.digest(),
                "pageCode", pageCode, "locale", locale, "sourceVersions", sourceVersions, "publishedOverrideRevisions", revisionCopy));
        return boundedResponse(new DdcI18nBundleResult(pageCode, locale, defaultLocale, version, Collections.unmodifiableMap(messages), descriptor.scope(),
                descriptor.activationVersion(), Collections.unmodifiableMap(sourceVersions), revisionCopy, List.copyOf(diagnostics)));
    }

    private PermissionManifest.Message source(ActivationDescriptor descriptor, DdcI18nSourceMessageBO row) {
        if (row.getTenantId() == null || row.getCatalogScopeId() == null || !descriptor.scope().tenantId().equals(row.getTenantId().toString())
                || !descriptor.scope().catalogScopeId().equals(row.getCatalogScopeId().toString())
                || descriptor.producers().stream().noneMatch(producer -> producer.producerId().equals(row.getProducerId())
                && producer.sourceVersion().equals(row.getSourceVersion()))) throw corrupt();
        var message = new PermissionManifest.Message(row.getPageCode(), row.getMessageKey(), row.getDefaultLocale(), strings(row.getDefaultsJson()));
        if (!I18nSourceRules.sourceHash(message).equals(row.getSourceHash())) throw corrupt();
        return message;
    }

    private Map<String, String> compatible(PermissionManifest.Message message, String hash, DdcI18nOverrideEntryBO current,
            DdcI18nOverrideEntryBO matching, List<DdcI18nBundleResult.Diagnostic> diagnostics) {
        if (current != null) {
            Map<String, String> values = strings(current.getTranslationsJson());
            if (values.isEmpty() && hash.equals(current.getSourceHash())) return Map.of();
            if (!values.isEmpty() && placeholdersMatch(message, values)) {
                if (!hash.equals(current.getSourceHash())) diagnostics.add(new DdcI18nBundleResult.Diagnostic(message.pageCode(), message.key(), "SOURCE_CHANGED_REVIEW_REQUIRED"));
                return values;
            }
            if (!values.isEmpty()) diagnostics.add(new DdcI18nBundleResult.Diagnostic(message.pageCode(), message.key(), "PLACEHOLDER_CONFLICT"));
        }
        if (matching != null) {
            if (!hash.equals(matching.getSourceHash())) throw corrupt();
            Map<String, String> values = strings(matching.getTranslationsJson());
            if (values.isEmpty() || placeholdersMatch(message, values)) return values;
        }
        return Map.of();
    }

    private boolean placeholdersMatch(PermissionManifest.Message message, Map<String, String> values) {
        var expected = I18nSourceRules.placeholders(message.defaults().get(message.defaultLocale()));
        for (var entry : values.entrySet()) {
            try {
                I18nSourceRules.locale(entry.getKey());
                if (!expected.equals(I18nSourceRules.placeholders(entry.getValue()))) return false;
            } catch (IllegalArgumentException invalid) { return false; }
        }
        return true;
    }

    Map<String, String> strings(String value) {
        try {
            Map<String, Object> decoded = mapper.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() { });
            Map<String, String> result = new TreeMap<>();
            decoded.forEach((key, item) -> { if (!(item instanceof String text)) throw corrupt(); result.put(key, text); });
            return Collections.unmodifiableMap(result);
        } catch (IOException invalid) { throw corrupt(); }
    }

    <T> T boundedResponse(T response) {
        try {
            if (mapper.writeValueAsBytes(response).length > MAX_RESPONSE_BYTES) throw responseTooLarge();
            return response;
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { throw corrupt(); }
    }

    static top.egon.cola.component.common.core.exception.CommonException responseTooLarge() {
        return new top.egon.cola.component.common.core.exception.CommonException(56412, "TIANSHU_I18N_REQUEST_TOO_LARGE", "Selected metadata exceeds the 16 MiB response limit");
    }

    private String identity(String page, String key) { return page + "/" + key; }
    private static IllegalStateException corrupt() { return new IllegalStateException("TIANSHU_SELECTED_METADATA_INVALID"); }
}
