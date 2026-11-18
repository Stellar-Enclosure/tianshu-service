package top.egon.cola.component.tianshu.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import top.egon.cola.component.tianshu.format.DdcConfigCanonicalJson;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptCommand;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptStatusEnum;
import top.egon.cola.component.tianshu.model.config.DdcConfigSnapshotDTO;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DdcConfigDeliveryContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final String RESTART = "sha256:" + "a".repeat(64);
    private static final String CONTENT = "sha256:197290ef0e710598d3bd9708cda7033860f1dadef0afab616c44c4c367880a8c";
    private static final String BASELINE = "sha256:b9c8a6dff813aacea34218b9c2e158597517e8b78017c02267d406d3bc7b7960";
    private static final String RECEIPT = "sha256:ddb64d2b8a94f23c0661bef972e6ed370616c04ae86667c15e83e3a0bfec2124";
    private static final String SCOPE = """
            {"tenantId":"1001","bizCode":"reading","env":"prod","appCode":"reader","resourceName":"application.yml"}
            """.strip();
    private static final String SNAPSHOT_JSON = """
            {"schemaVersion":"1","scope":%s,"releaseRevision":"43","enabled":true,
             "restartDigest":"%s","contentSha256":"%s","hotValues":{"meta":null,"flags":{"rerank":false}},
             "dependencySnapshotId":"deps-r43","validUntil":"2026-11-18T16:00:00Z"}
            """.formatted(SCOPE, RESTART, CONTENT).strip();
    private static final String BASELINE_JSON = """
            {"schemaVersion":"1","scope":%s,"source":{"configId":"42","revision":"43"},"enabled":true,
             "contentSha256":"%s","restartDigest":"%s",
             "rules":[{"key":"flags.rerank","mode":"HOT","valueType":"BOOLEAN","schemaRef":null,
                       "nullable":false,"deletionAllowed":true},
                      {"key":"server.port","mode":"RESTART","valueType":"INTEGER","schemaRef":null,
                       "nullable":false,"deletionAllowed":false}],
             "targetBindings":[{"bindingId":"reader-prod","bindingRevision":"3","clusterId":"cluster-prod",
                                "kubernetesNamespace":"reader","workloadKind":"Deployment","workloadName":"reader",
                                "workloadUid":"35b95d17-722c-4b7c-a362-145074452cc0","containerName":"application"}],
             "restartValues":{"server.port":8080},"hotSeed":{"flags.rerank":false},
             "startupDependencies":{"sdk":"v6"},"secretReferences":{"database":{"name":"reader-db","version":"v3"}}}
            """.formatted(SCOPE, CONTENT, RESTART).strip();
    private static final String RECEIPT_JSON = """
            {"schemaVersion":"1","bootId":"boot-reader-a","releaseRevision":"43","reportSeq":"2",
             "appliedRevision":"43","contentSha256":"%s","appliedContentSha256":"%s",
             "baselineRestartDigest":"%s","status":"APPLIED","reportedAt":"2026-11-17T16:00:02Z",
             "appliedAt":"2026-11-17T16:00:02Z","receiptDigest":"%s","deliveryToken":"synthetic-token-a",
             "errorCode":null,"errorMessage":null}
            """.formatted(CONTENT, CONTENT, RESTART, RECEIPT).strip();

    @Test
    void explicitNullAndEmptyTextRemainDifferentDigestInputs() {
        assertThat(canonical("null")).isEqualTo("null");
        assertThat(canonical("\"\"")).isEqualTo("\"\"");
        assertThat(canonical("{\"present\":null}")).isNotEqualTo(canonical("{}"));
    }

    @Test
    void sortsUtf16PropertyNamesAndPreservesStringCodePointsAndArrayOrder() {
        String encoded = "{\"\uF000\":5,\"😀\":4,\"€\":3,\"a\":2,\"\\r\":1,\"é\":7,\"é\":6}";
        assertThat(canonical(encoded)).isEqualTo("{\"\\r\":1,\"a\":2,\"é\":7,\"é\":6,\"€\":3,\"😀\":4,\"\uF000\":5}");
        assertThat(canonical("[3,{\"y\":2,\"x\":1},\"\\u000F\\n\\\\\\\"\",0]"))
                .isEqualTo("[3,{\"x\":1,\"y\":2},\"\\u000f\\n\\\\\\\"\",0]");
    }

    @ParameterizedTest
    @CsvSource({
            "8000000000000000,0", "0000000000000001,5e-324", "8000000000000001,-5e-324",
            "7fefffffffffffff,1.7976931348623157e+308", "4340000000000000,9007199254740992",
            "4430000000000000,295147905179352830000", "44b52d02c7e14af6,1e+23",
            "44b52d02c7e14af7,1.0000000000000001e+23", "444b1ae4d6e2ef4f,999999999999999900000",
            "444b1ae4d6e2ef50,1e+21", "3eb0c6f7a0b5ed8c,9.999999999999997e-7",
            "3eb0c6f7a0b5ed8d,0.000001", "43143ff3c1cb0959,1424953923781206.2"
    })
    void emitsRfc8785Binary64BoundaryNumbers(String ieee754Bits, String expected) {
        double value = Double.longBitsToDouble(Long.parseUnsignedLong(ieee754Bits, 16));
        assertThat(text(DdcConfigCanonicalJson.canonicalBytes(DoubleNode.valueOf(value)))).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"k\":1,\"k\":2}", "{\"k\":{\"x\":1,\"x\":2}}", "{}[]", "NaN", "1e309",
            "\"\\ud800\"", "{\"\\udc00\":1}"})
    void parseRejectsNonIJsonAndTrailingValues(String invalid) {
        assertThatThrownBy(() -> DdcConfigCanonicalJson.parse(invalid.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidUtf8AndProgrammaticNonFiniteNumbers() {
        assertThatThrownBy(() -> DdcConfigCanonicalJson.parse(new byte[]{'"', (byte) 0xc3, 0x28, '"'}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DdcConfigCanonicalJson.canonicalBytes(DoubleNode.valueOf(Double.NaN)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void snapshotGoldenDigestAndOpaqueStrongEtagIgnoreTransportExpiry() throws JsonProcessingException {
        var snapshot = snapshot(SNAPSHOT_JSON);
        assertThat(snapshot.getReleaseRevision()).isEqualTo("43");
        assertThat(snapshot.getValidUntil()).isEqualTo(Instant.parse("2026-11-18T16:00:00Z"));
        assertThat(DdcConfigCanonicalJson.contentSha256(snapshot)).isEqualTo(CONTENT);
        assertThat(DdcConfigCanonicalJson.etag(snapshot)).isEqualTo("\"cfg-r43-" + CONTENT.substring(7) + "\"");
        var renewed = snapshot(SNAPSHOT_JSON.replace("16:00:00Z", "16:30:00Z"));
        assertThat(DdcConfigCanonicalJson.contentSha256(renewed)).isEqualTo(CONTENT);
        assertThat(DdcConfigCanonicalJson.etag(renewed)).isEqualTo(DdcConfigCanonicalJson.etag(snapshot));
        var revised = snapshot(SNAPSHOT_JSON.replace("\"43\"", "\"44\""));
        assertThat(DdcConfigCanonicalJson.contentSha256(revised)).isNotEqualTo(CONTENT);
        assertThat(DdcConfigCanonicalJson.etag(revised)).isNotEqualTo(DdcConfigCanonicalJson.etag(snapshot));
    }

    @Test
    void hotValuesKeepPresenceTypeAndDeepImmutability() throws JsonProcessingException {
        var snapshot = snapshot(SNAPSHOT_JSON);
        assertThat(snapshot.getHotValues().get("meta").isNull()).isTrue();
        assertThat(snapshot.getHotValues()).doesNotContainKey("absent");
        ObjectNode flags = (ObjectNode) snapshot.getHotValues().get("flags");
        flags.put("rerank", true);
        assertThat(snapshot.getHotValues().get("flags").get("rerank").booleanValue()).isFalse();
        assertThat(DdcConfigCanonicalJson.contentSha256(snapshot)).isEqualTo(CONTENT);
        assertThatThrownBy(() -> snapshot.getHotValues().put("new", flags)).isInstanceOf(UnsupportedOperationException.class);
        var emptyText = snapshot(SNAPSHOT_JSON.replace("\"meta\":null", "\"meta\":\"\""));
        assertThat(DdcConfigCanonicalJson.contentSha256(emptyText)).isNotEqualTo(CONTENT);
        var removed = snapshot(SNAPSHOT_JSON.replace("\"meta\":null,", ""));
        assertThat(DdcConfigCanonicalJson.contentSha256(removed)).isNotEqualTo(CONTENT);
    }

    @Test
    void distinguishesDisabledEmptySnapshotFromMissingStateAndRejectsImpreciseIds() throws JsonProcessingException {
        var disabled = snapshot(SNAPSHOT_JSON.replace("\"enabled\":true", "\"enabled\":false")
                .replace("{\"meta\":null,\"flags\":{\"rerank\":false}}", "{}"));
        assertThat(disabled.getEnabled()).isFalse();
        assertThat(disabled.getHotValues()).isEmpty();
        assertThat(DdcConfigCanonicalJson.contentSha256(disabled)).isNotEqualTo(CONTENT);
        assertThatThrownBy(() -> snapshot(SNAPSHOT_JSON.replace("\"enabled\":true,", "")))
                .isInstanceOf(JsonProcessingException.class);
        assertThatThrownBy(() -> snapshot(SNAPSHOT_JSON.replace("\"enabled\":true", "\"enabled\":false")))
                .isInstanceOf(JsonProcessingException.class);
        assertThatThrownBy(() -> snapshot(SNAPSHOT_JSON.replace("\"enabled\":true", "\"enabled\":\"false\"")))
                .isInstanceOf(JsonProcessingException.class);
        assertThatThrownBy(() -> snapshot(SNAPSHOT_JSON.replace("\"releaseRevision\":\"43\"", "\"releaseRevision\":43")))
                .isInstanceOf(JsonProcessingException.class);
        var exact = snapshot(SNAPSHOT_JSON.replace("\"43\"", "\"9007199254740993\""));
        assertThat(exact.getReleaseRevision()).isEqualTo("9007199254740993");
        assertThat(MAPPER.readTree(MAPPER.writeValueAsString(exact)).get("releaseRevision").isTextual()).isTrue();
        assertThatThrownBy(() -> snapshot(SNAPSHOT_JSON.replace("\"43\"", "\"9223372036854775808\"")))
                .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void configurationWireTypesAndFieldNamesRejectMapperCoercion() {
        assertThatThrownBy(() -> snapshot(SNAPSHOT_JSON.replace("\"reading\"", "42")))
                .isInstanceOf(JsonProcessingException.class);
        assertThatThrownBy(() -> snapshot(SNAPSHOT_JSON.replace("\"2026-11-18T16:00:00Z\"", "1795017600")))
                .isInstanceOf(JsonProcessingException.class);
        assertThatThrownBy(() -> snapshot(SNAPSHOT_JSON.replace("16:00:00Z", "17:00:00+01:00")))
                .isInstanceOf(JsonProcessingException.class);
        String injected = SNAPSHOT_JSON.replace("\"enabled\":true", "\"enabled\":true,\"podUid\":\"forged\"");
        ObjectMapper permissive = MAPPER.copy()
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        assertThatThrownBy(() -> permissive.readValue(injected, DdcConfigSnapshotDTO.class))
                .isInstanceOf(JsonProcessingException.class);
        assertThatThrownBy(() -> receipt(RECEIPT_JSON.replace("16:00:02Z", "17:00:02+01:00")))
                .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void restartDigestCoversRestartRulesDependenciesAndSecretReferenceVersionsOnly() throws JsonProcessingException {
        assertThat(DdcConfigCanonicalJson.restartDigest(baseline(BASELINE_JSON))).isEqualTo(BASELINE);
        String alteredHot = BASELINE_JSON.replace("\"flags.rerank\":false", "\"flags.rerank\":true");
        assertThat(DdcConfigCanonicalJson.restartDigest(baseline(alteredHot))).isEqualTo(BASELINE);
        for (String material : new String[]{BASELINE_JSON.replace("8080", "8081"),
                BASELINE_JSON.replace("\"sdk\":\"v6\"", "\"sdk\":\"v7\""), BASELINE_JSON.replace("\"v3\"", "\"v4\"")}) {
            assertThat(DdcConfigCanonicalJson.restartDigest(baseline(material))).isNotEqualTo(BASELINE);
        }
        assertThatThrownBy(() -> baseline(BASELINE_JSON.replace("\"name\":\"reader-db\"", "\"secretValue\":\"plain\"")))
                .isInstanceOf(JsonProcessingException.class);
        assertThatThrownBy(() -> baseline(BASELINE_JSON.replace("\"restartValues\":{\"server.port\":8080}", "\"restartValues\":null")))
                .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void receiptDigestExcludesDeliveryCredentialAndChangesWhenBusinessSequenceChanges() throws JsonProcessingException {
        var report = receipt(RECEIPT_JSON);
        assertThat(report.getStatus()).isEqualTo(DdcConfigReceiptStatusEnum.APPLIED);
        assertThat(report.getReportedAt()).isEqualTo(Instant.parse("2026-11-17T16:00:02Z"));
        assertThat(report.toString()).doesNotContain("synthetic-token-a");
        assertThat(DdcConfigCanonicalJson.receiptDigest(report)).isEqualTo(RECEIPT);
        var reauthorized = receipt(RECEIPT_JSON.replace("synthetic-token-a", "synthetic-token-b"));
        assertThat(DdcConfigCanonicalJson.receiptDigest(reauthorized)).isEqualTo(RECEIPT);
        var later = receipt(RECEIPT_JSON.replace("\"reportSeq\":\"2\"", "\"reportSeq\":\"3\""));
        assertThat(DdcConfigCanonicalJson.receiptDigest(later)).isNotEqualTo(RECEIPT);
        assertThatThrownBy(() -> receipt(RECEIPT_JSON.replace("\"reportSeq\":\"2\"", "\"reportSeq\":2")))
                .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void receiptConstructionDiagnosticsRedactCredentialsAndUntrustedErrorText() {
        String debug = DdcConfigReceiptCommand.builder()
                .deliveryToken("synthetic-receipt-token").errorMessage("synthetic-untrusted-text").toString();
        assertThat(debug).doesNotContain("synthetic-receipt-token", "synthetic-untrusted-text");
    }

    @Test
    void receiptBeanValidationRejectsImpossibleAppliedStateAndKeepsKnownWireStatuses() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(receipt(RECEIPT_JSON))).isEmpty();
            assertThat(validator.validate(receipt(RECEIPT_JSON.replace("\"appliedRevision\":\"43\"", "\"appliedRevision\":\"42\""))))
                    .isNotEmpty();

            String requiredRestart = RECEIPT_JSON.replace("\"status\":\"APPLIED\"", "\"status\":\"RESTART_REQUIRED\"")
                    .replace("\"appliedRevision\":\"43\"", "\"appliedRevision\":\"42\"")
                    .replace("\"appliedAt\":\"2026-11-17T16:00:02Z\"", "\"appliedAt\":null")
                    .replace("\"errorCode\":null", "\"errorCode\":\"RESTART_DIGEST_MISMATCH\"");
            assertThat(validator.validate(receipt(requiredRestart))).isEmpty();
            assertThat(validator.validate(receipt(requiredRestart.replace("\"RESTART_DIGEST_MISMATCH\"", "null"))))
                    .isNotEmpty();
        }
        assertThat(MAPPER.writeValueAsString(DdcConfigReceiptStatusEnum.DISABLED_APPLIED)).isEqualTo("\"DISABLED_APPLIED\"");
        assertThatThrownBy(() -> receipt(RECEIPT_JSON.replace("\"APPLIED\"", "\"READY\"")))
                .isInstanceOf(JsonProcessingException.class);
    }

    private static DdcConfigSnapshotDTO snapshot(String json) throws JsonProcessingException {
        return MAPPER.readValue(json, DdcConfigSnapshotDTO.class);
    }

    private static DdcConfigBaselineVO baseline(String json) throws JsonProcessingException {
        return MAPPER.readValue(json, DdcConfigBaselineVO.class);
    }

    private static DdcConfigReceiptCommand receipt(String json) throws JsonProcessingException {
        return MAPPER.readValue(json, DdcConfigReceiptCommand.class);
    }

    private static String canonical(String json) {
        JsonNode input = DdcConfigCanonicalJson.parse(json.getBytes(StandardCharsets.UTF_8));
        return text(DdcConfigCanonicalJson.canonicalBytes(input));
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
