package top.egon.cola.component.tianshu.admin.service.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.common.id.snowflake.SnowflakeIdGenerator;
import top.egon.cola.component.tianshu.admin.common.DdcAdminException;
import top.egon.cola.component.tianshu.admin.config.DdcAdminProperties;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigItemBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigVersionBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcOperationLogBO;
import top.egon.cola.component.tianshu.admin.model.dto.DdcConfigCreateRequest;
import top.egon.cola.component.tianshu.admin.model.dto.DdcConfigQueryRequest;
import top.egon.cola.component.tianshu.admin.model.dto.DdcConfigPreviewQuery;
import top.egon.cola.component.tianshu.admin.model.vo.DdcConfigPreviewVO.ConfigChangeEnum;
import top.egon.cola.component.tianshu.admin.model.dto.DdcConfigRollbackRequest;
import top.egon.cola.component.tianshu.admin.model.dto.DdcConfigUpdateRequest;
import top.egon.cola.component.tianshu.admin.model.enums.ChangeType;
import top.egon.cola.component.tianshu.admin.model.vo.DdcConfigVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcConfigVersionVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigItemRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigVersionRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcNamespaceEnvAppBindingRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcOperationLogRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcPublishTaskRepository;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigItemConverterImpl;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigVersionConverterImpl;
import top.egon.cola.component.tianshu.model.config.DdcConfigValue;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import top.egon.cola.component.tianshu.admin.service.publish.DdcConfigManifestFactory;
import top.egon.cola.component.tianshu.admin.security.tenancy.*;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DdcConfigServiceTest {

    @BeforeAll
    static void initializeIdGenerator() {
        SnowflakeIdGenerator.initialize(0L, Duration.ofMillis(5));
    }

    private DdcConfigItemRepository itemRepository;
    private DdcConfigVersionRepository versionRepository;
    private DdcOperationLogRepository operationLogRepository;
    private DdcNamespaceEnvAppBindingRepository bindingRepository;
    private DdcPublishTaskRepository publishTaskRepository;
    private DdcConfigService service;
    private DdcTenantRegistry tenants;
    private final DdcConfigManifestFactory manifests = new DdcConfigManifestFactory(new ObjectMapper());
    private static final DdcTenantContextDTO TENANT = new DdcTenantContextDTO(401L,"registered","user:test");
    private static final DdcConfigBaselineVO.TargetBindingVO TARGET = new DdcConfigBaselineVO.TargetBindingVO(
            "binding-a","7","cluster-test","reader-apps","Deployment","reader","deployment-uid","application");
    private final DdcConfigManifestFactory.PublicationPolicyResolver policies = (tenant,scope) ->
            new DdcConfigManifestFactory.PublicationPolicyVO(scope,List.of(),List.of(TARGET),null,Map.of(),Map.of());
    private DdcTenantContextExecutor.Scope tenantScope;

    @BeforeEach
    void setUp() {
        tenantScope = new DdcTenantContextExecutor().bind(TENANT);
        itemRepository = mock(DdcConfigItemRepository.class);
        versionRepository = mock(DdcConfigVersionRepository.class);
        operationLogRepository = mock(DdcOperationLogRepository.class);
        bindingRepository = mock(DdcNamespaceEnvAppBindingRepository.class);
        publishTaskRepository = mock(DdcPublishTaskRepository.class);
        ObjectProvider<DdcAdminProperties> properties =
                new DefaultListableBeanFactory().getBeanProvider(DdcAdminProperties.class);
        tenants = mock(DdcTenantRegistry.class);
        when(tenants.requireEnabledTenant(401L)).thenReturn(new DdcTenantBindingDTO(401L,"registered",true));
        service = new DdcConfigService(itemRepository, versionRepository, new DdcConfigItemConverterImpl(),
                new DdcConfigVersionConverterImpl(), operationLogRepository, bindingRepository,
                publishTaskRepository, new DdcYamlConfigValidator(1_048_576), manifests, policies, tenants);
        when(itemRepository.insert(any(DdcConfigItemBO.class))).thenAnswer(invocation -> {
            DdcConfigItemBO item = invocation.getArgument(0);
            item.setId(101L).setTenantId(401L).setPublicId("config-public-1").setVersion(0L);
            return item;
        });
        when(itemRepository.update(any(DdcConfigItemBO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(versionRepository.insert(any(DdcConfigVersionBO.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach void restoreTenant() { tenantScope.close(); }

    @Test
    void previewsAgainstFrozenPublishedRevisionWithoutWritingOrReturningValues() {
        String prior = "feature:\n  preexisting: 42\n  removed: true\n";
        String candidate = "feature:\n  preexisting: 84\n  added: false\n";
        var existing = item(101L, "config-public-1", 4L, 2L, false, "feature: {}");
        var scope = new DdcConfigScopeVO("401", "infra", "prod", "gateway", "application.yml");
        var published = manifests.freeze(TENANT, existing, 2L, prior, true, policies.require(TENANT, scope));
        when(itemRepository.findByPublicId("config-public-1")).thenReturn(Optional.of(existing));
        when(versionRepository.findByConfigItemIdAndRevision(101L, 2L))
                .thenReturn(Optional.of(version(101L, "config-public-1", 2L, prior, "UPDATE")
                        .setTenantId(401L).setBizCode("infra").setEnv("prod").setAppCode("gateway")
                        .setManifestJson(published.manifest())));
        var query = DdcConfigPreviewQuery.builder().configId("config-public-1").expectedVersion(4L)
                .bizCode("infra").env("prod").appCode("gateway").content(candidate).build();

        var result = service.preview(query);

        assertThat(result.publishedRevision()).isEqualTo("2");
        assertThat(result.rolloutRequired()).isTrue();
        assertThat(result.changes()).hasSize(3);
        assertThat(result.changes()).anyMatch(change ->
                change.key().equals("feature.removed") && change.change() == ConfigChangeEnum.REMOVED);
        assertThat(result.targets()).extracting(target -> target.bindingId()).containsExactly("binding-a");
        assertThatThrownBy(() -> service.preview(query.setExpectedVersion(3L)))
                .hasMessage("TIANSHU_CONFIG_PREVIEW_REVISION_MISMATCH");
        verify(itemRepository, never()).update(any(DdcConfigItemBO.class));
        verify(versionRepository, never()).insert(any(DdcConfigVersionBO.class));
        verifyNoInteractions(operationLogRepository, publishTaskRepository);
    }

    @Test
    void hotOnlyPreviewDoesNotRequireRolloutAndReturnsOnlyTheApprovedSecretReference() {
        String prior = "feature:\n  enabled: false\n  workerCount: 2\n";
        var publishedScope = new DdcConfigScopeVO("401", "infra", "prod", "gateway", "application.yml");
        var rule = new DdcConfigBaselineVO.RuleVO("feature.enabled", DdcConfigBaselineVO.ReloadModeEnum.HOT,
                "BOOLEAN", null, false, true);
        var policy = new DdcConfigManifestFactory.PublicationPolicyVO(publishedScope, List.of(rule), List.of(TARGET),
                null, Map.of(), Map.of("db.password", new DdcConfigBaselineVO.SecretReferenceVO("test-db-ref", "5")));
        var serviceWithHotRule = new DdcConfigService(itemRepository, versionRepository,
                new DdcConfigItemConverterImpl(), new DdcConfigVersionConverterImpl(), operationLogRepository,
                bindingRepository, publishTaskRepository, new DdcYamlConfigValidator(1_048_576), manifests,
                (tenant, scope) -> policy, tenants);
        var existing = item(101L, "config-public-1", 4L, 2L, false, prior);
        var published = manifests.freeze(TENANT, existing, 2L, prior, true, policy);
        when(itemRepository.findByPublicId("config-public-1")).thenReturn(Optional.of(existing));
        when(versionRepository.findByConfigItemIdAndRevision(101L, 2L))
                .thenReturn(Optional.of(version(101L, "config-public-1", 2L, prior, "UPDATE")
                        .setTenantId(401L).setBizCode("infra").setEnv("prod").setAppCode("gateway")
                        .setManifestJson(published.manifest())));
        var query = DdcConfigPreviewQuery.builder().configId("config-public-1").expectedVersion(4L)
                .bizCode("infra").env("prod").appCode("gateway").content(prior.replace("false", "true")).build();

        var result = serviceWithHotRule.preview(query);

        assertThat(result.rolloutRequired()).isFalse();
        assertThat(result.targets()).isEmpty();
        assertThat(result.changes()).singleElement().satisfies(change -> {
            assertThat(change.key()).isEqualTo("feature.enabled");
            assertThat(change.mode()).isEqualTo(DdcConfigBaselineVO.ReloadModeEnum.HOT);
        });
        assertThat(result.secretReferences()).singleElement().satisfies(ref -> {
            assertThat(ref.key()).isEqualTo("db.password");
            assertThat(ref.name()).isEqualTo("test-db-ref");
            assertThat(ref.version()).isEqualTo("5");
        });
    }

    @Test
    void legacyConstructorCannotPersistAnEditBeforeDiscoveringMissingTrustedNativeAuthority() {
        var legacy = new DdcConfigService(itemRepository,versionRepository,new DdcConfigItemConverterImpl(),
                new DdcConfigVersionConverterImpl(),operationLogRepository,bindingRepository,publishTaskRepository,
                new DefaultListableBeanFactory().getBeanProvider(DdcAdminProperties.class));
        assertThatThrownBy(() -> legacy.create(configRequest("infra","prod","gateway"),"test"))
                .hasMessage("TIANSHU_TRUSTED_TENANT_REQUIRED");
        verifyNoInteractions(itemRepository,versionRepository,operationLogRepository);
    }

    @Test
    void createsConfigAndVersionWithSeparatePublicAndInternalIds() {
        DdcConfigVO result = service.create(configRequest("infra", "prod", "gateway"), "tester");

        assertThat(result.getId()).isEqualTo("config-public-1");
        assertThat(result.getCurrentVersion()).isEqualTo(1L);
        assertThat(result.getDeleted()).isFalse();
        var version = org.mockito.ArgumentCaptor.forClass(DdcConfigVersionBO.class);
        verify(versionRepository).insert(version.capture());
        assertThat(version.getValue().getConfigItemId()).isEqualTo(101L);
        assertThat(version.getValue().getConfigId()).isEqualTo("config-public-1");
        assertThat(version.getValue().getConfigRevision()).isEqualTo(1L);
        assertThat(version.getValue().getChangeType()).isEqualTo(ChangeType.CREATE.name());
        assertThat(manifests.materialize(version.getValue(), new DdcConfigScopeVO("401","infra","prod","gateway","application.yml"))
                .baseline().getSource().getConfigId()).isEqualTo("101");
        var operation = org.mockito.ArgumentCaptor.forClass(DdcOperationLogBO.class);
        verify(operationLogRepository).insert(operation.capture());
        assertThat(operation.getValue().getBizCode()).isEqualTo("infra");
        assertThat(operation.getValue().getAppCode()).isEqualTo("gateway");
        assertThat(operation.getValue().getOperationType()).isEqualTo(ChangeType.CREATE.name());
    }

    @Test
    void upsertKeepsBusinessVersionAndClearsDefaultValueWithoutTouchingPublishedPointer() {
        DdcConfigItemBO existing = item(101L, "config-public-1", 3L, 2L, false, "old");
        existing.setDefaultValue("fallback");
        when(itemRepository.findByBizCodeAndEnvAndAppCode("infra", "prod", "gateway"))
                .thenReturn(List.of(existing));

        DdcConfigVO result = service.upsert(configRequest("infra", "prod", "gateway"), 3L, "tester");

        assertThat(result.getCurrentVersion()).isEqualTo(4L);
        assertThat(result.getContent()).contains("enabled: true");
        assertThat(existing.getDefaultValue()).isNull();
        assertThat(existing.getPublishedVersion()).isEqualTo(2L);
        var version = org.mockito.ArgumentCaptor.forClass(DdcConfigVersionBO.class);
        verify(versionRepository).insert(version.capture());
        assertThat(version.getValue().getConfigRevision()).isEqualTo(4L);
        assertThat(version.getValue().getOldContent()).isEqualTo("old");
    }

    @Test
    void createRefusesToReuseABusinessDeletedResourceUntilRollback() {
        when(itemRepository.findByBizCodeAndEnvAndAppCode("infra", "prod", "gateway"))
                .thenReturn(List.of(item(101L, "config-public-1", 4L, 2L, true, "old")));
        assertThatThrownBy(() -> service.upsert(configRequest("infra", "prod", "gateway"), 4L, "tester"))
                .isInstanceOf(DdcAdminException.class).hasMessage("deleted config resource cannot be reused");
        verify(itemRepository, never()).insert(any(DdcConfigItemBO.class));
        verify(itemRepository, never()).update(any(DdcConfigItemBO.class));
    }

    @Test
    void businessDeleteAndRollbackLeaveTechnicalDeletedAtUntouched() {
        DdcConfigItemBO deletedItem = item(101L, "config-public-1", 2L, 1L, false, "feature: {enabled: true}\n");
        when(itemRepository.findByPublicId("config-public-1")).thenReturn(Optional.of(deletedItem));
        DdcConfigVO deleted = service.delete("config-public-1", "tester", "remove");
        assertThat(deleted.getDeleted()).isTrue();
        assertThat(deleted.getEnabled()).isFalse();
        assertThat(deletedItem.getDeletedAt()).isNull();
        assertThat(deletedItem.getCurrentVersion()).isEqualTo(3L);

        DdcConfigVersionBO target = version(101L, "config-public-1", 1L, "feature:\n  enabled: false\n",
                ChangeType.CREATE.name());
        target.setTenantId(401L).setBizCode("infra").setEnv("prod").setAppCode("gateway");
        var scope = new DdcConfigScopeVO("401","infra","prod","gateway","application.yml");
        var historical = new DdcConfigManifestFactory.PublicationPolicyVO(scope,List.of(),List.of(TARGET),null,
                Map.of("driver","old-fixed-version"),Map.of("database",new DdcConfigBaselineVO.SecretReferenceVO("db-access","7")));
        target.setManifestJson(manifests.freeze(TENANT,deletedItem,1L,target.getNewContent(),true,historical).manifest());
        when(versionRepository.findByConfigIdAndVersion("config-public-1", 1L)).thenReturn(Optional.of(target));
        DdcConfigVO restored = service.rollback(new DdcConfigRollbackRequest("config-public-1", 1L, "restore"),
                "tester");
        assertThat(restored.getDeleted()).isFalse();
        assertThat(restored.getEnabled()).isTrue();
        assertThat(restored.getContent()).isEqualTo("feature:\n  enabled: false\n");
        assertThat(deletedItem.getDeletedAt()).isNull();
        assertThat(deletedItem.getCurrentVersion()).isEqualTo(4L);
        var written=org.mockito.ArgumentCaptor.forClass(DdcConfigVersionBO.class);
        verify(versionRepository,org.mockito.Mockito.times(2)).insert(written.capture());
        var disabled=written.getAllValues().getFirst();
        assertThat(disabled.getNewContent()).isEqualTo("feature: {enabled: true}\n");
        assertThat(disabled.getManifestJson().path("enabled").booleanValue()).isFalse();
        var restoredFixed=manifests.materialize(written.getAllValues().getLast(),scope).baseline();
        assertThat(restoredFixed.getStartupDependencies()).containsEntry("driver","old-fixed-version");
        assertThat(restoredFixed.getSecretReferences().get("database").getVersion()).isEqualTo("7");
    }

    @Test void legacyUnmanifestedRollbackIsReadOnlyAndCannotCreateANativeVersion() {
        var current=item(101L,"config-public-1",2L,1L,false,"feature: {enabled: true}\n");
        when(itemRepository.findByPublicId("config-public-1")).thenReturn(Optional.of(current));
        when(versionRepository.findByConfigIdAndVersion("config-public-1",1L))
                .thenReturn(Optional.of(version(101L,"config-public-1",1L,"feature: {enabled: false}\n","CREATE")));
        assertThatThrownBy(() -> service.rollback(new DdcConfigRollbackRequest("config-public-1",1L,"restore"),"test"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(itemRepository,never()).update(any());
        verify(versionRepository,never()).insert(any());
    }

    @Test
    void searchAndPagePreserveFiltersVisibilityAndPageEnvelope() {
        DdcConfigItemBO item = item(101L, "config-public-1", 2L, 2L, false, "runtime");
        when(itemRepository.search("infra", "team-a", "prod", "gateway", null, false)).thenReturn(List.of(item));
        when(bindingRepository.findVisibleNamespaceCodes("infra", "prod", "gateway")).thenReturn(List.of("team-a"));
        when(itemRepository.search(eq("infra"), eq("team-a"), eq("prod"), eq("gateway"), isNull(),
                eq(false), any(PageQuery.class))).thenReturn(DdcPageResult.<DdcConfigItemBO>builder()
                        .records(List.of(item)).total(12).pageNo(2).pageSize(10).build());
        DdcConfigQueryRequest query = new DdcConfigQueryRequest();
        query.setBizCode(" infra ");
        query.setNamespaceCode(" team-a ");
        query.setEnv(" prod ");
        query.setAppCode(" gateway ");
        query.setIncludeDeleted(false);

        DdcConfigVO listed = service.list(query).getFirst();
        DdcPageResult<DdcConfigVO> page = service.page(query, new PageQuery(2, 10));

        assertThat(listed.getId()).isEqualTo("config-public-1");
        assertThat(listed.getVisibleNamespaces()).containsExactly("team-a");
        assertThat(page.getTotal()).isEqualTo(12);
        assertThat(page.getPageNo()).isEqualTo(2);
        assertThat(page.getRecords().getFirst().getId()).isEqualTo("config-public-1");
    }

    @Test
    void pullUsesPublishedBusinessRevisionRatherThanTechnicalVersion() {
        DdcConfigItemBO item = item(101L, "config-public-1", 8L, 5L, false, "draft");
        when(itemRepository.findByBizCodeAndEnvAndAppCode("infra", "prod", "gateway")).thenReturn(List.of(item));
        when(versionRepository.findByConfigItemIdAndRevision(101L, 5L)).thenReturn(Optional.of(
                version(101L, "config-public-1", 5L, "published", ChangeType.UPDATE.name())));

        List<DdcConfigValue> result = service.pull("infra", "prod", "gateway");

        assertThat(result).singleElement().satisfies(value -> {
            assertThat(value.getVersion()).isEqualTo(5L);
            assertThat(value.getContent()).isEqualTo("published");
        });
        verify(versionRepository).findByConfigItemIdAndRevision(101L, 5L);
        verifyNoInteractions(publishTaskRepository);
    }

    @Test
    void versionListAndPageProjectConfigRevisionToTheLegacyVersionField() {
        DdcConfigVersionBO version = version(101L, "config-public-1", 5L, "published", ChangeType.UPDATE.name());
        when(versionRepository.findByConfigIdOrderByVersionDesc("config-public-1")).thenReturn(List.of(version));
        when(versionRepository.findByConfigIdOrderByVersionDescIdDesc(eq("config-public-1"), any(PageQuery.class)))
                .thenReturn(DdcPageResult.<DdcConfigVersionBO>builder().records(List.of(version))
                        .total(1).pageNo(1).pageSize(10).build());

        DdcConfigVersionVO row = service.versions("config-public-1").getFirst();
        DdcPageResult<DdcConfigVersionVO> page = service.pageVersions("config-public-1", new PageQuery(1, 10));

        assertThat(row.getId()).isEqualTo(version.getPublicId());
        assertThat(row.getConfigId()).isEqualTo("config-public-1");
        assertThat(row.getVersion()).isEqualTo(5L);
        assertThat(page.getRecords().getFirst().getVersion()).isEqualTo(5L);
    }

    private DdcConfigCreateRequest configRequest(String biz, String env, String app) {
        return new DdcConfigCreateRequest(biz, env, app, null, "application.yml",
                "feature:\n  enabled: true\n", "YAML", "config test");
    }

    private DdcConfigItemBO item(Long id, String publicId, Long currentVersion, Long publishedVersion,
            boolean deleted, String content) {
        return DdcConfigItemBO.builder().id(id).tenantId(401L).publicId(publicId).bizCode("infra")
                .appCode("gateway").env("prod").namespace(null).resourceName("application.yml")
                .content(content).format("YAML").currentVersion(currentVersion).publishedVersion(publishedVersion)
                .enabled(!deleted).deleted(deleted).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .version(0L).build();
    }

    private DdcConfigVersionBO version(Long configItemId, String configPublicId, Long revision,
            String content, String changeType) {
        return DdcConfigVersionBO.builder().id(revision + 100L).publicId("version-" + revision)
                .configItemId(configItemId).configId(configPublicId).configRevision(revision)
                .resourceName("application.yml").newContent(content).format("YAML").changeType(changeType)
                .createdAt(LocalDateTime.now()).version(0L).build();
    }
}
