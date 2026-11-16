package top.egon.cola.component.tianshu.admin.service.si.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiReferenceBO;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;

/** SI 四态异构资源规格校验、确定性摘要计算、引用抽取与检索文本抽取策略契约。 */
public interface DdcSiContentStrategy {

    int CODE_VALIDATION_FAILED = 56422;

    boolean supports(DdcSiKindEnum kind);

    NormalizedContent validateAndNormalize(DdcSiKindEnum kind, String rawSpecJson);

    List<DdcSiReferenceBO> extractReferences(DdcSiKindEnum kind, String normalizedSpecJson);

    String extractSearchableText(DdcSiKindEnum kind, String normalizedSpecJson, String displayName, String description);

    /** 统一 JSON 解析与键排序归一化并计算 SHA-256 摘要，消除重复实现。 */
    default NormalizedContent canonicalizeJson(ObjectMapper mapper, JsonNode tree) {
        try {
            JsonNode sorted = sortObjectKeys(mapper, tree);
            String canonical = mapper.writeValueAsString(sorted);
            return NormalizedContent.builder()
                    .normalizedJson(canonical)
                    .digestSha256(sha256Hex(canonical))
                    .build();
        } catch (Exception failure) {
            throw new CommonException(CODE_VALIDATION_FAILED, "TIANSHU_SI_SPEC_INVALID",
                    "failed to canonicalize SI spec JSON", failure);
        }
    }

    static String sha256Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private static JsonNode sortObjectKeys(ObjectMapper mapper, JsonNode node) {
        if (node == null || !node.isContainerNode()) {
            return node;
        }
        if (node.isObject()) {
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            node.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), sortObjectKeys(mapper, entry.getValue())));
            ObjectNode copy = mapper.createObjectNode();
            sorted.forEach(copy::set);
            return copy;
        }
        ArrayNode arrayCopy = mapper.createArrayNode();
        node.forEach(child -> arrayCopy.add(sortObjectKeys(mapper, child)));
        return arrayCopy;
    }

    /** 规格规范化输出及内容摘要承载对象。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(chain = true)
    @Builder
    class NormalizedContent {
        private String normalizedJson;
        private String digestSha256;
    }
}
