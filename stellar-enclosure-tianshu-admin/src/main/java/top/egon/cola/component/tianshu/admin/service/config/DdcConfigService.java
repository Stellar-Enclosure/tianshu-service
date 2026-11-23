package top.egon.cola.component.tianshu.admin.service.config;

import jakarta.validation.Valid;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogEvent;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogPhaseEnum;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogOutcomeEnum;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogRecorder;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.tianshu.admin.common.DdcAdminException;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigItemBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcConfigVersionBO;
import top.egon.cola.component.tianshu.admin.model.dto.DdcConfigCreateRequest;
import top.egon.cola.component.tianshu.admin.model.dto.DdcConfigQueryRequest;
import top.egon.cola.component.tianshu.admin.model.dto.DdcConfigPreviewQuery;
import top.egon.cola.component.tianshu.admin.model.dto.DdcConfigRollbackRequest;
import top.egon.cola.component.tianshu.admin.model.dto.DdcConfigUpdateRequest;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO;
import top.egon.cola.component.tianshu.admin.model.enums.ChangeType;
import top.egon.cola.component.tianshu.admin.model.enums.PublishStatus;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigItemConverter;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcConfigVersionConverter;
import top.egon.cola.component.tianshu.admin.model.vo.DdcConfigVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcConfigVersionVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcConfigPreviewVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcConfigPreviewVO.ConfigChangeEnum;
import top.egon.cola.component.tianshu.admin.model.vo.DdcConfigPreviewVO.ConfigChangeVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcConfigPreviewVO.ConfigSecretReferenceVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcConfigPreviewVO.ConfigTargetVO;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigItemRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcConfigVersionRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcNamespaceEnvAppBindingRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcPublishTaskRepository;
import top.egon.cola.component.tianshu.format.DdcConfigFormatStrategyRegistry;
import top.egon.cola.component.tianshu.format.DdcYamlConfigFormatStrategy;
import top.egon.cola.component.tianshu.model.config.DdcConfigFormat;
import top.egon.cola.component.tianshu.model.config.DdcConfigValue;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO.RuleVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO.ReloadModeEnum;
import top.egon.cola.component.tianshu.admin.service.publish.DdcConfigManifestFactory;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service("ddcConfigService")
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcConfigService {

    public static final String DEFAULT_RESOURCE_NAME = DdcYamlConfigFormatStrategy.DEFAULT_RESOURCE_NAME;
    public static final String YAML_FORMAT = DdcConfigFormat.YAML.name();

    private static final Method CREATE_METHOD = sourceMethod("create", DdcConfigCreateRequest.class, String.class);
    private static final Method UPSERT_METHOD = sourceMethod("upsert", DdcConfigCreateRequest.class, Long.class, String.class);
    private static final Method UPDATE_METHOD = sourceMethod("update", DdcConfigUpdateRequest.class, String.class);
    private static final Method DELETE_BY_ID_METHOD = sourceMethod("delete", String.class, String.class, String.class);
    private static final Method DELETE_BY_SCOPE_METHOD = sourceMethod("delete", String.class, String.class, String.class, Long.class, String.class, String.class);
    private static final Method ROLLBACK_METHOD = sourceMethod("rollback", DdcConfigRollbackRequest.class, String.class);

    private static final Set<String> SUPPORTED_RESOURCE_NAMES = Set.of(DEFAULT_RESOURCE_NAME);
    private static final List<String> PREPARED_PUBLISH_STATUSES = List.of(
            PublishStatus.PUBLISHING.name(), PublishStatus.UNKNOWN.name());

    @Qualifier("ddcConfigItemRepository") private final DdcConfigItemRepository configItemRepository;
    @Qualifier("ddcConfigVersionRepository") private final DdcConfigVersionRepository versionRepository;
    @Qualifier("ddcConfigItemConverterImpl") private final DdcConfigItemConverter itemConverter;
    @Qualifier("ddcConfigVersionConverterImpl") private final DdcConfigVersionConverter versionConverter;
    @Qualifier("operationLogRecorder") private final OperationLogRecorder operationLogRecorder;
    @Qualifier("ddcNamespaceEnvAppBindingRepository") private final DdcNamespaceEnvAppBindingRepository bindingRepository;
    @Qualifier("ddcPublishTaskRepository") private final DdcPublishTaskRepository publishTaskRepository;
    @Qualifier("ddcYamlConfigValidator") private final DdcYamlConfigValidator yamlValidator;
    @Qualifier("ddcConfigManifestFactory") private final DdcConfigManifestFactory manifests;
    @Qualifier("ddcConfigPublicationPolicyResolver") private final DdcConfigManifestFactory.PublicationPolicyResolver policies;
    @Qualifier("ddcTenantRegistry") private final DdcTenantRegistry tenants;

    @Transactional(readOnly = true)
    public DdcConfigPreviewVO preview(@Valid DdcConfigPreviewQuery query) {
        var tenant = currentTenant();
        yamlValidator.validate(DEFAULT_RESOURCE_NAME, YAML_FORMAT, query.getContent());
        var requestedScope = new DdcConfigScopeVO(Long.toString(tenant.tenantId()), query.getBizCode(),
                query.getEnv(), query.getAppCode(), DEFAULT_RESOURCE_NAME);
        if ((query.getConfigId() == null) != (query.getExpectedVersion() == null)) {
            throw new DdcAdminException("TIANSHU_CONFIG_PREVIEW_ID_VERSION_REQUIRED");
        }
        if (query.getConfigId() == null) {
            if (findScopeConfig(query.getBizCode(), query.getEnv(), query.getAppCode()).isPresent()) {
                throw new DdcAdminException("TIANSHU_CONFIG_PREVIEW_SCOPE_ALREADY_EXISTS");
            }
            var policy = policies.require(tenant, requestedScope);
            try {
                var projection = DdcConfigSnapshotService.projectValues(query.getContent(), true, policy.rules());
                return previewResult(null, projection.rules(), projection.hotValues(), projection.restartValues(),
                        policy, true);
            } catch (IOException invalid) {
                throw new DdcAdminException("TIANSHU_CONFIG_PREVIEW_INVALID");
            }
        }
        var item = getConfig(query.getConfigId());
        if (!Objects.equals(item.getTenantId(), tenant.tenantId()) || !requestedScope.equals(scope(tenant, item))
                || !Objects.equals(item.getCurrentVersion(), query.getExpectedVersion())) {
            throw new DdcAdminException("TIANSHU_CONFIG_PREVIEW_REVISION_MISMATCH");
        }
        var policy = policies.require(tenant, requestedScope);
        var published = item.getPublishedVersion() == null ? null
                : manifests.materialize(versionRepository.findByConfigItemIdAndRevision(item.getId(), item.getPublishedVersion())
                        .orElseThrow(() -> new DdcAdminException("TIANSHU_CONFIG_PREVIEW_BASELINE_UNAVAILABLE")), requestedScope)
                .baseline();
        var candidate = manifests.freeze(tenant, item, item.getCurrentVersion(), query.getContent(),
                !Boolean.TRUE.equals(item.getDeleted()) && Boolean.TRUE.equals(item.getEnabled()), policy).baseline();
        boolean rolloutRequired = published == null || !published.getRestartDigest().equals(candidate.getRestartDigest())
                || !published.getTargetBindings().equals(candidate.getTargetBindings());
        return previewResult(published, candidate.getRules(), candidate.getHotSeed(), candidate.getRestartValues(),
                policy, rolloutRequired);
    }

    private DdcConfigPreviewVO previewResult(DdcConfigBaselineVO published, List<RuleVO> rules,
            Map<String, JsonNode> hotValues, Map<String, JsonNode> restartValues,
            DdcConfigManifestFactory.PublicationPolicyVO policy, boolean rolloutRequired) {
        Map<String, JsonNode> before = new LinkedHashMap<>();
        if (published != null) {
            before.putAll(published.getHotSeed());
            before.putAll(published.getRestartValues());
        }
        Map<String, JsonNode> after = new LinkedHashMap<>(restartValues);
        after.putAll(hotValues);
        Map<String, RuleVO> priorRules = published == null ? Map.of() : byKey(published.getRules());
        Map<String, RuleVO> candidateRules = byKey(rules);
        var keys = new TreeSet<>(before.keySet());
        keys.addAll(after.keySet());
        var changes = new ArrayList<ConfigChangeVO>();
        for (String key : keys) {
            ConfigChangeEnum change;
            if (!before.containsKey(key)) change = ConfigChangeEnum.ADDED;
            else if (!after.containsKey(key)) change = ConfigChangeEnum.REMOVED;
            else if (!Objects.equals(before.get(key), after.get(key))) change = ConfigChangeEnum.UPDATED;
            else if (!Objects.equals(priorRules.get(key), candidateRules.get(key))) change = ConfigChangeEnum.RULE_CHANGED;
            else continue;
            RuleVO currentRule = candidateRules.getOrDefault(key, priorRules.get(key));
            ReloadModeEnum mode = currentRule == null ? ReloadModeEnum.RESTART : currentRule.getMode();
            changes.add(new ConfigChangeVO(key, mode, change));
        }
        var references = policy.secretReferences().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(item -> new ConfigSecretReferenceVO(item.getKey(), item.getValue().getName(), item.getValue().getVersion()))
                .toList();
        var targets = rolloutRequired ? policy.targetBindings().stream().map(binding -> new ConfigTargetVO(
                binding.getBindingId(), binding.getClusterId(), binding.getKubernetesNamespace(), binding.getWorkloadName()))
                .toList() : List.<ConfigTargetVO>of();
        return new DdcConfigPreviewVO(published == null ? null : published.getSource().getRevision(),
                rolloutRequired, changes, references, targets);
    }

    private Map<String, RuleVO> byKey(List<RuleVO> rules) {
        return rules.stream().collect(Collectors.toMap(RuleVO::getKey, Function.identity()));
    }

    @Transactional
    public DdcConfigVO create(@Valid DdcConfigCreateRequest request, String operator) {
        long startedAt = System.nanoTime();
        currentTenant();
        validateDraft(request);
        findScopeConfig(request.getBizCode(), request.getEnv(), request.getAppCode())
                .ifPresent(item -> { throw new DdcAdminException("config item already exists"); });
        DdcConfigItemBO saved = createDraft(request, operator);
        saveOperation(saved, "TIANSHU_CONFIG_CREATE", CREATE_METHOD, startedAt);
        return itemConverter.toConfigVO(saved);
    }

    @Transactional
    public DdcConfigVO upsert(@Valid DdcConfigCreateRequest request, Long expectedVersion, String operator) {
        long startedAt = System.nanoTime();
        currentTenant();
        validateDraft(request);
        DdcConfigItemBO existing = findScopeConfig(request.getBizCode(), request.getEnv(), request.getAppCode())
                .orElse(null);
        if (existing == null) {
            if (expectedVersion != null && expectedVersion != 0L) throw new DdcAdminException("config version changed");
            DdcConfigItemBO saved = createDraft(request, operator);
            saveOperation(saved, "TIANSHU_CONFIG_UPSERT", UPSERT_METHOD, startedAt);
            return itemConverter.toConfigVO(saved);
        }
        if (Boolean.TRUE.equals(existing.getDeleted())) throw new DdcAdminException("deleted config resource cannot be reused");
        if (!Objects.equals(request.getResourceName(), existing.getResourceName())
                || !Objects.equals(request.getFormat(), existing.getFormat())) {
            throw new DdcAdminException("config resource name and format cannot be changed");
        }
        if (expectedVersion == null || !Objects.equals(expectedVersion, existing.getCurrentVersion()))
            throw new DdcAdminException("config version changed");

        String oldContent = existing.getContent();
        existing.setContent(request.getContent()).setDefaultValue(null).setDescription(request.getDescription())
                .setCurrentVersion(existing.getCurrentVersion() + 1).setUpdatedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
        DdcConfigItemBO saved = configItemRepository.update(existing);
        saveVersion(saved, oldContent, saved.getContent(), ChangeType.UPDATE, "upsert config draft", operator);
        saveOperation(saved, "TIANSHU_CONFIG_UPSERT", UPSERT_METHOD, startedAt);
        return itemConverter.toConfigVO(saved);
    }

    private DdcConfigItemBO createDraft(DdcConfigCreateRequest request, String operator) {
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        DdcConfigItemBO command = DdcConfigItemBO.builder()
                .bizCode(request.getBizCode()).appCode(request.getAppCode()).env(request.getEnv())
                .resourceName(request.getResourceName()).content(request.getContent()).defaultValue(null)
                .format(request.getFormat()).description(request.getDescription()).currentVersion(1L)
                .publishedVersion(null).enabled(true).deleted(false).createdAt(now).updatedAt(now).build();
        DdcConfigItemBO saved = configItemRepository.insert(command);
        saveVersion(saved, null, saved.getContent(), ChangeType.CREATE, "create config", operator);
        return saved;
    }

    @Transactional
    public DdcConfigVO update(@Valid DdcConfigUpdateRequest request, String operator) {
        long startedAt = System.nanoTime();
        currentTenant();
        if (request == null) throw new DdcAdminException("config update request is required");
        DdcConfigItemBO item = getConfig(request.getId());
        yamlValidator.validate(item.getResourceName(), item.getFormat(), request.getContent());
        if (request.getCurrentVersion() != null && !Objects.equals(request.getCurrentVersion(), item.getCurrentVersion()))
            throw new DdcAdminException("config version changed");
        String oldContent = item.getContent();
        item.setContent(request.getContent()).setCurrentVersion(item.getCurrentVersion() + 1)
                .setUpdatedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
        DdcConfigItemBO saved = configItemRepository.update(item);
        saveVersion(saved, oldContent, saved.getContent(), ChangeType.UPDATE, request.getChangeReason(), operator);
        saveOperation(saved, "TIANSHU_CONFIG_UPDATE", UPDATE_METHOD, startedAt);
        return itemConverter.toConfigVO(saved);
    }

    @Transactional
    public DdcConfigVO delete(String configPublicId, String operator, String reason) {
        long startedAt = System.nanoTime();
        currentTenant();
        DdcConfigItemBO item = getConfig(configPublicId);
        if (Boolean.TRUE.equals(item.getDeleted())) return config(item);
        return delete(item, operator, reason, DELETE_BY_ID_METHOD, startedAt);
    }

    @Transactional
    public DdcConfigVO delete(String bizCode, String env, String appCode, Long expectedVersion,
            String operator, String reason) {
        long startedAt = System.nanoTime();
        currentTenant();
        DdcConfigItemBO item = findScopeConfig(bizCode, env, appCode).orElse(null);
        if (item == null) return null;
        if (Boolean.TRUE.equals(item.getDeleted())) return config(item);
        if (expectedVersion == null || !Objects.equals(expectedVersion, item.getCurrentVersion()))
            throw new DdcAdminException("config version changed");
        return delete(item, operator, reason, DELETE_BY_SCOPE_METHOD, startedAt);
    }

    private DdcConfigVO delete(DdcConfigItemBO item, String operator, String reason, Method sourceMethod, long startedAt) {
        String oldContent = item.getContent();
        item.setDeleted(true).setEnabled(false).setCurrentVersion(item.getCurrentVersion() + 1)
                .setUpdatedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
        DdcConfigItemBO saved = configItemRepository.update(item);
        saveVersion(saved, oldContent, oldContent, ChangeType.DELETE, reason, operator);
        saveOperation(saved, "TIANSHU_CONFIG_DELETE", sourceMethod, startedAt);
        return config(saved);
    }

    @Transactional
    public DdcConfigVO rollback(@Valid DdcConfigRollbackRequest request, String operator) {
        long startedAt = System.nanoTime();
        var tenant = currentTenant();
        DdcConfigItemBO item = getConfig(request.getConfigId());
        DdcConfigVersionBO target = versionRepository.findByConfigIdAndVersion(request.getConfigId(), request.getVersion())
                .orElseThrow(() -> new DdcAdminException("config version not found"));
        var fixed = manifests.materialize(target, scope(tenant, item)).baseline();
        var currentlyAuthorized = policies.require(tenant, scope(tenant, item));
        if (!currentlyAuthorized.targetBindings().containsAll(fixed.getTargetBindings())) {
            throw new DdcAdminException("historical config target is no longer authorized");
        }
        var dependencies = new java.util.LinkedHashMap<>(fixed.getStartupDependencies());
        dependencies.remove(DdcConfigManifestFactory.HOT_SCHEMA_DEPENDENCY_KEY);
        var fixedPolicy = new DdcConfigManifestFactory.PublicationPolicyVO(fixed.getScope(), fixed.getRules(),
                fixed.getTargetBindings(), fixed.getDependencySnapshotId(), dependencies, fixed.getSecretReferences());
        yamlValidator.validate(target.getResourceName(), target.getFormat(), target.getNewContent());
        String oldContent = item.getContent();
        item.setContent(target.getNewContent()).setDeleted(false).setEnabled(fixed.getEnabled())
                .setCurrentVersion(item.getCurrentVersion() + 1).setUpdatedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
        DdcConfigItemBO saved = configItemRepository.update(item);
        saveVersion(saved, oldContent, saved.getContent(), ChangeType.ROLLBACK, request.getReason(), operator, fixedPolicy);
        saveOperation(saved, "TIANSHU_CONFIG_ROLLBACK", ROLLBACK_METHOD, startedAt);
        return config(saved);
    }

    public List<DdcConfigVO> list(@Valid DdcConfigQueryRequest request) {
        DdcConfigQueryRequest query = request == null ? new DdcConfigQueryRequest() : request;
        return configItemRepository.search(optional(query.getBizCode()), optional(query.getNamespaceCode()),
                        optional(query.getEnv()), optional(query.getAppCode()), null, query.isIncludeDeleted())
                .stream().map(this::config).toList();
    }

    public DdcPageResult<DdcConfigVO> page(@Valid DdcConfigQueryRequest request, PageQuery pageQuery) {
        DdcConfigQueryRequest query = request == null ? new DdcConfigQueryRequest() : request;
        DdcPageResult<DdcConfigItemBO> page = configItemRepository.search(optional(query.getBizCode()),
                optional(query.getNamespaceCode()), optional(query.getEnv()), optional(query.getAppCode()), null,
                query.isIncludeDeleted(), pageQuery);
        return DdcPageResult.<DdcConfigVO>builder().records(page.getRecords().stream().map(this::config).toList())
                .total(page.getTotal()).pageNo(page.getPageNo()).pageSize(page.getPageSize()).build();
    }

    public DdcConfigVO get(String configPublicId) { return config(getConfig(configPublicId)); }

    public Optional<DdcConfigVO> find(String bizCode, String env, String appCode) {
        return findScopeConfig(bizCode, env, appCode).map(this::config);
    }

    public List<DdcConfigValue> pull(String bizCode, String env, String appCode) {
        return pull(bizCode, env, appCode, null, null);
    }

    public List<DdcConfigValue> pull(String bizCode, String env, String appCode, String resourceName,
            Long targetVersion) {
        return findScopeConfig(bizCode, env, appCode)
                .flatMap(item -> requestedVersion(item, resourceName, targetVersion))
                .filter(this::isRuntimeValue).map(this::toConfigValue).map(List::of).orElseGet(List::of);
    }

    private Optional<DdcConfigVersionBO> requestedVersion(DdcConfigItemBO item, String resourceName,
            Long targetVersion) {
        if (resourceName == null && targetVersion == null) return publishedVersion(item);
        if (resourceName == null || targetVersion == null || targetVersion <= 0
                || !Objects.equals(resourceName, item.getResourceName())) return Optional.empty();
        boolean published = Objects.equals(targetVersion, item.getPublishedVersion());
        boolean prepared = !published && Objects.equals(targetVersion, item.getCurrentVersion())
                && publishTaskRepository != null
                && publishTaskRepository.findFirstByBizCodeAndEnvAndAppCodeAndResourceNameAndStatusIn(
                        item.getBizCode(), item.getEnv(), item.getAppCode(), item.getResourceName(),
                        PREPARED_PUBLISH_STATUSES)
                .map(DdcPublishTaskBO::getTargetVersion).filter(targetVersion::equals).isPresent();
        if (!published && !prepared) return Optional.empty();
        return versionRepository.findByConfigItemIdAndRevision(item.getId(), targetVersion);
    }

    public List<DdcConfigVersionVO> versions(String configPublicId) {
        return versionRepository.findByConfigIdOrderByVersionDesc(configPublicId).stream()
                .map(versionConverter::toVersionVO).toList();
    }

    public DdcPageResult<DdcConfigVersionVO> pageVersions(String configPublicId, PageQuery pageQuery) {
        DdcPageResult<DdcConfigVersionBO> page = versionRepository
                .findByConfigIdOrderByVersionDescIdDesc(configPublicId, pageQuery);
        return DdcPageResult.<DdcConfigVersionVO>builder()
                .records(page.getRecords().stream().map(versionConverter::toVersionVO).toList())
                .total(page.getTotal()).pageNo(page.getPageNo()).pageSize(page.getPageSize()).build();
    }

    private DdcConfigItemBO getConfig(String configPublicId) {
        return configItemRepository.findByPublicId(configPublicId)
                .orElseThrow(() -> new DdcAdminException("config item not found"));
    }

    private void saveVersion(DdcConfigItemBO item, String oldContent, String newContent, ChangeType changeType,
            String reason, String operator) {
        var tenant = currentTenant();
        var scope = scope(tenant, item);
        saveVersion(item, oldContent, newContent, changeType, reason, operator, policies.require(tenant, scope));
    }

    private void saveVersion(DdcConfigItemBO item, String oldContent, String newContent, ChangeType changeType,
            String reason, String operator, DdcConfigManifestFactory.PublicationPolicyVO policy) {
        var tenant = currentTenant();
        var frozen = manifests.freeze(tenant, item, item.getCurrentVersion(), newContent,
                !Boolean.TRUE.equals(item.getDeleted()) && Boolean.TRUE.equals(item.getEnabled()), policy);
        DdcConfigVersionBO version = DdcConfigVersionBO.builder()
                .tenantId(tenant.tenantId()).configItemId(item.getId()).configId(item.getPublicId()).bizCode(item.getBizCode())
                .appCode(item.getAppCode()).env(item.getEnv()).namespace(null).resourceName(item.getResourceName())
                .configRevision(item.getCurrentVersion()).oldContent(oldContent).newContent(newContent)
                .manifestJson(frozen.manifest()).format(item.getFormat()).changeType(changeType.name()).changeReason(reason).operator(operator)
                .createdAt(LocalDateTime.now(java.time.ZoneOffset.UTC)).build();
        versionRepository.insert(version);
    }

    private DdcTenantContextDTO currentTenant() {
        try {
            if (tenants == null) { throw new IllegalArgumentException(); }
            String value = MDC.get("tenantId");
            long id = Long.parseLong(value);
            if (!Long.toString(id).equals(value)) { throw new IllegalArgumentException(); }
            var registered = tenants.requireEnabledTenant(id);
            return new DdcTenantContextDTO(id, registered.getIdentityTenantId(), MDC.get("userId"));
        } catch (RuntimeException invalid) { throw new DdcAdminException("TIANSHU_TRUSTED_TENANT_REQUIRED"); }
    }

    private static DdcConfigScopeVO scope(DdcTenantContextDTO tenant, DdcConfigItemBO item) {
        return new DdcConfigScopeVO(Long.toString(tenant.tenantId()), item.getBizCode(), item.getEnv(), item.getAppCode(), item.getResourceName());
    }

    private void saveOperation(DdcConfigItemBO item, String actionCode, Method sourceMethod, long startedAt) {
        operationLogRecorder.recordInTransaction(OperationLogEvent.builder()
                .moduleCode("CONFIG").actionCode(actionCode).resourceType("CONFIG").resourceId(item.getPublicId())
                .bizCode(item.getBizCode()).env(item.getEnv()).appCode(item.getAppCode())
                .phase(OperationLogPhaseEnum.COMMAND).outcome(OperationLogOutcomeEnum.SUCCEEDED)
                .durationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt))
                .details(JsonNodeFactory.instance.objectNode().put("revision", Long.toString(item.getCurrentVersion())))
                .build(), sourceMethod);
    }

    private static Method sourceMethod(String name, Class<?>... parameters) {
        try {
            return DdcConfigService.class.getMethod(name, parameters);
        } catch (NoSuchMethodException invalidDeclaration) {
            throw new IllegalStateException("TIANSHU_CONFIG_OPERATION_METHOD_INVALID", invalidDeclaration);
        }
    }

    private Optional<DdcConfigVersionBO> publishedVersion(DdcConfigItemBO item) {
        if (item.getPublishedVersion() == null) return Optional.empty();
        return Optional.of(versionRepository.findByConfigItemIdAndRevision(item.getId(), item.getPublishedVersion())
                .orElseThrow(() -> new DdcAdminException("published config version not found")));
    }

    private DdcConfigValue toConfigValue(DdcConfigVersionBO version) {
        DdcConfigValue value = new DdcConfigValue();
        value.setResourceName(version.getResourceName());
        value.setContent(version.getNewContent());
        value.setFormat(version.getFormat());
        value.setVersion(version.getConfigRevision());
        return value;
    }

    private boolean isRuntimeValue(DdcConfigVersionBO version) {
        return !ChangeType.DELETE.name().equals(version.getChangeType());
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new DdcAdminException(fieldName + " is required");
    }

    private void validateDraft(DdcConfigCreateRequest request) {
        if (request == null) throw new DdcAdminException("config request is required");
        requireText(request.getBizCode(), "bizCode");
        requireText(request.getAppCode(), "appCode");
        requireText(request.getEnv(), "env");
        requireText(request.getResourceName(), "resourceName");
        requireText(request.getFormat(), "format");
        if (!SUPPORTED_RESOURCE_NAMES.contains(request.getResourceName()))
            throw new DdcAdminException("resourceName must be application.yml or application.yaml");
        try {
            DdcConfigFormatStrategyRegistry.defaults().get(request.getFormat(), request.getResourceName());
        } catch (IllegalArgumentException failure) {
            throw new DdcAdminException("unsupported config resource format");
        }
        yamlValidator.validate(request.getResourceName(), request.getFormat(), request.getContent());
    }

    private Optional<DdcConfigItemBO> findScopeConfig(String bizCode, String env, String appCode) {
        List<DdcConfigItemBO> items = configItemRepository.findByBizCodeAndEnvAndAppCode(bizCode, env, appCode);
        if (items.size() > 1) throw new DdcAdminException("Tianshu scope contains multiple configuration resources");
        return items.stream().findFirst();
    }

    private DdcConfigVO config(DdcConfigItemBO item) {
        DdcConfigVO value = itemConverter.toConfigVO(item);
        if (bindingRepository != null) {
            value.setVisibleNamespaces(bindingRepository.findVisibleNamespaceCodes(
                    item.getBizCode(), item.getEnv(), item.getAppCode()));
        }
        return value;
    }

    private String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
