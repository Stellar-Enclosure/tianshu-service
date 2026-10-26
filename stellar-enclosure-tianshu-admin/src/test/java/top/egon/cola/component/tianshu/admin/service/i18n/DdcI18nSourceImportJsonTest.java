package top.egon.cola.component.tianshu.admin.service.i18n;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.SourceImportReceipt;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static top.egon.cola.component.tianshu.admin.service.i18n.DdcSourceImportTestFixture.*;

class DdcI18nSourceImportJsonTest {
    private final DdcI18nSourceImportJson json = new DdcI18nSourceImportJson();

    @Test void readsTheSharedManifestIncludingLargeStringIdsAndMultipleMappingTargets() {
        var request = request();
        assertThat(json.read(bytes(body(request)))).isEqualTo(request);
        assertThat(json.read(bytes(body(request))).resourceIds().values()).contains("9007199254740993");
    }

    @Test void rejectsUnknownEnvelopeAndManifestProperties() {
        var body = body(request());
        body.put("publishHumanDraft", true);
        assertThatThrownBy(() -> json.read(bytes(body))).isInstanceOf(IllegalArgumentException.class);
        body.remove("publishHumanDraft");
        nested(body, "manifest").put("ignoreMissingResources", true);
        assertThatThrownBy(() -> json.read(bytes(body))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unknown property");
    }

    @Test void neverCoercesNumericIdsOrUnknownScopeProperties() {
        var invalidTenant = body(request());
        nested(invalidTenant, "scope").put("tenantId", 101);
        assertThatThrownBy(() -> json.read(bytes(invalidTenant))).isInstanceOf(IllegalArgumentException.class);
        var body = body(request());
        nested(body, "resourceIds").put("order.api", 17);
        var numeric = body;
        assertThatThrownBy(() -> json.read(bytes(numeric))).isInstanceOf(IllegalArgumentException.class);
        nested(body, "scope").put("namespaceId", "untrusted");
        var unknown = body;
        assertThatThrownBy(() -> json.read(bytes(unknown))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsDuplicateKeysTrailingTokensAndUnboundedNesting() {
        String valid = new String(bytes(body(request())), StandardCharsets.UTF_8);
        String duplicate = "{\"operationId\":\"32fb3f12-7ce5-4e19-8665-a5db0d9c1baa\"," + valid.substring(1);
        assertThatThrownBy(() -> json.read(duplicate.getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> json.read((valid + " {}").getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> json.read(("{\"a\":".repeat(20) + "0" + "}".repeat(20)).getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsOversizedBodiesBeforeJsonConversion() {
        assertThatThrownBy(() -> json.read(new byte[DdcI18nSourceImportJson.MAX_BODY_BYTES + 1]))
                .isInstanceOf(DdcI18nSourceImportJson.BodyTooLargeException.class);
    }

    @Test void persistsAndReadsTheExactImmutableReceipt() {
        var request = request();
        var receipt = new SourceImportReceipt("901", request.operationId(), request.scope(), request.producerId(), request.producerVersion(),
                request.sourceVersion(), request.digests(), request.projectionDigest(), 7, 3, 10, NOW.plusNanos(123_000));
        String stored = json.writeReceipt(receipt);
        assertThat(json.readReceipt(stored)).isEqualTo(receipt);
        assertThat(json.readReceipt(stored).matches(request)).isTrue();
        assertThatThrownBy(() -> json.readReceipt(stored.replace("\"receiptId\":\"901\"", "\"receiptId\":901")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> body, String field) { return (Map<String, Object>) body.get(field); }
}
