package top.egon.cola.component.tianshu.admin.service.i18n;

import org.junit.jupiter.api.Test;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nSourceMessageBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcI18nOverrideEntryBO;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ActivationDescriptor;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CanonicalJson;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.I18nSourceRules;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static top.egon.cola.component.tianshu.admin.service.i18n.DdcSourceImportTestFixture.*;

class DdcI18nBundleAssemblerTest {
    private final DdcI18nBundleAssembler assembler = new DdcI18nBundleAssembler();

    @Test void resolvesEachKeyBeforeCommonAndFollowsRequestedThenDefaultLocaleOrder() {
        var shared = source("common", "title", Map.of("en-US", "Common", "zh-CN", "公共"));
        var page = source("orders", "title", Map.of("en-US", "Source", "zh-CN", "源"));
        var second = source("orders", "second", Map.of("zh-CN", "第二"));
        var commonOnly = source("common", "save", Map.of("zh-CN", "保存"));
        var result = assembler.assemble(descriptor(), "orders", "en-US", List.of(shared, page, second, commonOnly),
                List.of(entry(page, "CURRENT", page.getSourceHash(), Map.of("zh-CN", "人工默认")),
                        entry(second, "CURRENT", second.getSourceHash(), Map.of("zh-CN", "人工第二"))), Map.of("orders", "1"));
        assertThat(result.messages()).containsEntry("title", "Source").containsEntry("second", "人工第二").containsEntry("save", "保存");
        var translated = assembler.assemble(descriptor(), "orders", "en-US", List.of(page),
                List.of(entry(page, "CURRENT", page.getSourceHash(), Map.of("en-US", "Human"))), Map.of("orders", "2"));
        assertThat(translated.messages()).containsEntry("title", "Human");
    }

    @Test void changedSourceKeepsCompatibleTranslationAndReportsReview() {
        var row = source("orders", "title", Map.of("zh-CN", "源 {{name}}"));
        var result = assembler.assemble(descriptor(), "orders", "en-US", List.of(row),
                List.of(entry(row, "CURRENT", "a".repeat(64), Map.of("en-US", "Human {{name}}"))), Map.of("orders", "2"));
        assertThat(result.messages()).containsEntry("title", "Human {{name}}");
        assertThat(result.diagnostics()).extracting(d -> d.reason()).containsExactly("SOURCE_CHANGED_REVIEW_REQUIRED");
    }

    @Test void incompatibleNewOverrideUsesExactHistoricalSourceAndOtherwiseSourceDefault() {
        var row = source("orders", "title", Map.of("zh-CN", "源 {{name}}"));
        var incompatible = entry(row, "CURRENT", "b".repeat(64), Map.of("en-US", "New {{amount}}"));
        var old = entry(row, "MATCHING", row.getSourceHash(), Map.of("en-US", "Old {{name}}"));
        var result = assembler.assemble(descriptor(), "orders", "en-US", List.of(row), List.of(incompatible, old), Map.of("orders", "3"));
        assertThat(result.messages()).containsEntry("title", "Old {{name}}");
        assertThat(result.diagnostics()).extracting(d -> d.reason()).containsExactly("PLACEHOLDER_CONFLICT");
        assertThat(assembler.assemble(descriptor(), "orders", "en-US", List.of(row), List.of(incompatible), Map.of("orders", "3")).messages())
                .containsEntry("title", "源 {{name}}");
    }

    @Test void oldRunningVersionKeepsHistoryWhenNewActivationRemovedItsKey() {
        var old = source("orders", "retired", Map.of("zh-CN", "旧源"));
        var retained = entry(old, "CURRENT", old.getSourceHash(), Map.of("en-US", "Retained human"));
        var result = assembler.assemble(descriptor(), "orders", "en-US", List.of(old), List.of(retained), Map.of("orders", "5"));
        assertThat(result.messages()).containsEntry("retired", "Retained human");
        var newer = source("orders", "new", Map.of("zh-CN", "新源"));
        assertThat(assembler.assemble(descriptor(), "orders", "en-US", List.of(newer), List.of(retained), Map.of("orders", "5")).messages())
                .doesNotContainKey("retired").containsEntry("new", "新源");
    }

    @Test void emptyPublicationClearsOnlyItsReviewedSourceDomainAndDoesNotReviveHistory() {
        var row = source("orders", "title", Map.of("zh-CN", "源"));
        var old = entry(row, "MATCHING", row.getSourceHash(), Map.of("en-US", "Earlier"));
        var clear = entry(row, "CURRENT", row.getSourceHash(), Map.of());
        assertThat(assembler.assemble(descriptor(), "orders", "en-US", List.of(row), List.of(clear, old), Map.of("orders", "3")).messages())
                .containsEntry("title", "源");
        clear.setSourceHash("c".repeat(64));
        assertThat(assembler.assemble(descriptor(), "orders", "en-US", List.of(row), List.of(clear, old), Map.of("orders", "3")).messages())
                .containsEntry("title", "Earlier");
    }

    @Test void publicationChangesVersionWithoutChangingDescriptorAndScopeCorruptionFailsClosed() {
        var row = source("orders", "title", Map.of("zh-CN", "源"));
        var first = assembler.assemble(descriptor(), "orders", "en-US", List.of(row), List.of(), Map.of("orders", "1", "common", "0"));
        var next = assembler.assemble(descriptor(), "orders", "en-US", List.of(row), List.of(), Map.of("orders", "2", "common", "0"));
        assertThat(first.version()).isNotEqualTo(next.version());
        assertThat(first.catalogActivationVersion()).isEqualTo("9");
        row.setTenantId(102L);
        assertThatThrownBy(() -> assembler.assemble(descriptor(), "orders", "en-US", List.of(row), List.of(), Map.of())).isInstanceOf(IllegalStateException.class);
    }

    static ActivationDescriptor descriptor() {
        var request = request();
        return new ActivationDescriptor(SCOPE, "9", request.operationId(), "7", NOW, List.of(new ActivationDescriptor.ProducerVersion(
                request.producerId(), "1", request.sourceVersion(), request.digests(), "901", "f".repeat(64))));
    }

    static DdcI18nSourceMessageBO source(String page, String key, Map<String, String> defaults) {
        var message = new PermissionManifest.Message(page, key, "zh-CN", defaults);
        return DdcI18nSourceMessageBO.builder().tenantId(101L).catalogScopeId(501L).sourceId(701L).producerId("order-admin")
                .sourceVersion(request().sourceVersion()).pageCode(page).messageKey(key).defaultLocale("zh-CN")
                .sourceHash(I18nSourceRules.sourceHash(message)).defaultsJson(CanonicalJson.write(defaults)).build();
    }

    static DdcI18nOverrideEntryBO entry(DdcI18nSourceMessageBO row, String selection, String sourceHash, Map<String, String> translations) {
        return new DdcI18nOverrideEntryBO(row.getPageCode(), row.getMessageKey(), sourceHash, 2L, CanonicalJson.write(translations), selection);
    }
}
