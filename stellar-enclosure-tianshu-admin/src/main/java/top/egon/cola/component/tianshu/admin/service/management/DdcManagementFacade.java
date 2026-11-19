package top.egon.cola.component.tianshu.admin.service.management;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.tianshu.admin.config.DdcServiceObservationBindings;
import top.egon.cola.component.tianshu.admin.model.vo.DdcServiceObservationBindingVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcServiceObservationViewVO;
import top.egon.cola.component.tianshu.admin.repository.DdcServiceObservationRepository;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import org.springframework.stereotype.Service;
import top.egon.cola.component.tianshu.admin.common.DdcAdminException;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.tianshu.admin.model.dto.DdcConfigCreateRequest;
import top.egon.cola.component.tianshu.admin.model.dto.DdcPublishRequest;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishAckBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcAppBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcBizBO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcConfigVO;
import top.egon.cola.component.tianshu.admin.repository.DdcPublishAckRepository;
import top.egon.cola.component.tianshu.admin.repository.DdcPublishTaskRepository;
import top.egon.cola.component.tianshu.admin.service.config.DdcConfigService;
import top.egon.cola.component.tianshu.admin.service.metadata.DdcNamespaceEnvAppBindingService;
import top.egon.cola.component.tianshu.admin.service.metadata.DdcAppService;
import top.egon.cola.component.tianshu.admin.service.metadata.DdcBizService;
import top.egon.cola.component.tianshu.admin.service.metadata.DdcScopeGate;
import top.egon.cola.component.tianshu.admin.service.publish.DdcPublishService;
import top.egon.cola.component.tianshu.error.management.DdcManagementErrorCode;
import top.egon.cola.component.tianshu.error.DdcErrorStatus;
import top.egon.cola.component.tianshu.model.management.DdcManagementConfig;
import top.egon.cola.component.tianshu.model.management.DdcManagementConfigDeleteRequest;
import top.egon.cola.component.tianshu.model.management.DdcManagementConfigQuery;
import top.egon.cola.component.tianshu.model.management.DdcManagementConfigUpsertRequest;
import top.egon.cola.component.tianshu.model.management.DdcManagementApp;
import top.egon.cola.component.tianshu.model.management.DdcManagementAppQuery;
import top.egon.cola.component.tianshu.model.management.DdcManagementBiz;
import top.egon.cola.component.tianshu.model.management.DdcManagementBizLookup;
import top.egon.cola.component.tianshu.model.management.DdcManagementBizQuery;
import top.egon.cola.component.tianshu.model.management.DdcManagementPublishRequest;
import top.egon.cola.component.tianshu.model.management.DdcManagementPublishResult;
import top.egon.cola.component.tianshu.model.management.DdcManagementPublishStatus;
import top.egon.cola.component.tianshu.model.management.DdcManagementPublishTarget;
import top.egon.cola.component.tianshu.model.management.DdcManagementPublishTask;
import top.egon.cola.component.tianshu.model.management.DdcManagementScopeBinding;
import top.egon.cola.component.tianshu.model.management.DdcManagementScopeQuery;
import top.egon.cola.component.tianshu.model.management.DdcManagementServiceCatalog;
import top.egon.cola.component.tianshu.model.management.DdcManagementServiceInstance;
import top.egon.cola.component.tianshu.model.management.DdcManagementServiceKey;
import top.egon.cola.component.tianshu.model.management.DdcManagementServiceQuery;
import top.egon.cola.component.tianshu.model.management.DdcManagementServiceSnapshot;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Validated
@Service("ddcManagementFacade")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcManagementFacade {

    @Qualifier("ddcConfigService") private final DdcConfigService configService;
    @Qualifier("ddcPublishService") private final DdcPublishService publishService;
    @Qualifier("ddcPublishTaskRepository") private final DdcPublishTaskRepository publishTaskRepository;
    @Qualifier("ddcPublishAckRepository") private final DdcPublishAckRepository publishAckRepository;
    @Qualifier("ddcScopeGate") private final DdcScopeGate scopeGate;
    @Qualifier("ddcNamespaceEnvAppBindingService") private final DdcNamespaceEnvAppBindingService bindingService;
    @Qualifier("ddcBizService") private final DdcBizService bizService;
    @Qualifier("ddcAppService") private final DdcAppService appService;
    @Qualifier("ddcServiceObservationBindings") private final DdcServiceObservationBindings serviceBindings;
    @Qualifier("ddcServiceObservationRepository") private final DdcServiceObservationRepository serviceObservations;
    @Qualifier("ddcTenantRegistry") private final DdcTenantRegistry tenants;
    @Qualifier("ddcPublishClock") private final Clock clock;

    public Optional<DdcManagementBiz> getBiz(DdcManagementBizLookup lookup) {
        require(lookup, "business lookup");
        if (bizService == null) {
            return Optional.empty();
        }
        DdcBizBO entity;
        if (lookup.id() != null && !lookup.id().isBlank()) {
            entity = bizService.findById(lookup.id()).orElse(null);
        } else if (lookup.bizCode() != null && !lookup.bizCode().isBlank()) {
            entity = findBizByCode(lookup.bizCode());
        } else {
            throw new DdcAdminException(
                    "business id or bizCode is required");
        }
        return Optional.ofNullable(entity).map(this::business);
    }

    public List<DdcManagementBiz> listBizs(DdcManagementBizQuery query) {
        require(query, "business query");
        if (bizService == null) {
            return List.of();
        }
        return bizService.list(query.keyword(), query.enabled()).stream()
                .map(this::business)
                .toList();
    }

    public Optional<DdcManagementApp> getApp(String ddcApplicationId) {
        requireText(ddcApplicationId, "ddcApplicationId");
        if (appService == null || bizService == null) {
            return Optional.empty();
        }
        return appService.findById(ddcApplicationId)
                .flatMap(app -> Optional.ofNullable(findBizByCode(app.getBizCode()))
                        .map(biz -> application(app, biz)));
    }

    public List<DdcManagementApp> listApps(DdcManagementAppQuery query) {
        require(query, "application query");
        if (appService == null || bizService == null) {
            return List.of();
        }
        String bizCode = query.bizCode();
        DdcBizBO selectedBiz = null;
        if (query.businessId() != null && !query.businessId().isBlank()) {
            selectedBiz = bizService.findById(query.businessId()).orElse(null);
            if (selectedBiz == null) {
                return List.of();
            }
            if (bizCode != null && !bizCode.isBlank()
                    && !bizCode.equals(selectedBiz.getBizCode())) {
                return List.of();
            }
            bizCode = selectedBiz.getBizCode();
        } else if (bizCode != null && !bizCode.isBlank()) {
            selectedBiz = findBizByCode(bizCode);
            if (selectedBiz == null) {
                return List.of();
            }
        }
        DdcBizBO fixedBiz = selectedBiz;
        return appService.list(bizCode, query.keyword(), query.enabled()).stream()
                .map(value -> fixedBiz != null
                        ? application(value, fixedBiz)
                        : application(value, findBizByCode(value.getBizCode())))
                .filter(Objects::nonNull)
                .toList();
    }

    public DdcManagementConfig findConfig(DdcManagementConfigQuery query) {
        require(query, "config query");
        scopeGate.assertPhysicalEnabled(
                requireText(query.bizCode(), "bizCode"),
                requireText(query.appCode(), "appCode"),
                requireText(query.env(), "env")
        );
        validateScope(
                query.bizCode(),
                query.env(),
                query.appCode()
        );
        return config(configService.find(
                query.bizCode(),
                query.env(),
                query.appCode()
        ).orElseThrow(() -> new DdcAdminException(
                DdcManagementErrorCode.CONFIG_NOT_FOUND
        )));
    }

    public DdcManagementConfig upsert(DdcManagementConfigUpsertRequest request) {
        require(request, "config upsert request");
        validateScope(
                request.bizCode(),
                request.env(),
                request.appCode()
        );
        requireText(request.operator(), "operator");
        DdcConfigVO saved = configService.upsert(
                new DdcConfigCreateRequest(
                        request.bizCode(),
                        request.env(),
                        request.appCode(),
                        null,
                        request.resourceName(),
                        request.content(),
                        request.format(),
                        request.description()
                ),
                request.expectedVersion(),
                request.operator()
        );
        return config(saved);
    }

    public void delete(DdcManagementConfigDeleteRequest request) {
        require(request, "config delete request");
        validateScope(
                request.bizCode(),
                request.env(),
                request.appCode()
        );
        requireText(request.operator(), "operator");
        configService.delete(
                request.bizCode(),
                request.env(),
                request.appCode(),
                request.expectedVersion(),
                request.operator(),
                request.reason()
        );
    }

    public DdcManagementPublishResult publish(DdcManagementPublishRequest request) {
        require(request, "publish request");
        validateScope(
                request.bizCode(),
                request.env(),
                request.appCode()
        );
        requireText(request.operator(), "operator");
        DdcPublishRequest command = new DdcPublishRequest();
        command.setChangeId(request.changeId());
        command.setBizCode(request.bizCode());
        command.setEnv(request.env());
        command.setAppCode(request.appCode());
        command.setResourceName(request.resourceName());
        command.setContent(request.content());
        command.setFormat(request.format());
        command.setExpectedVersion(request.expectedVersion());
        command.setTimeoutMs(request.timeoutMs());
        publishService.publish(command, request.operator());
        return result(getPublishTask(request.changeId()));
    }

    public DdcManagementPublishTask getPublishTask(String changeId) {
        requireText(changeId, "changeId");
        DdcPublishTaskBO task = publishTaskRepository.findByChangeId(changeId)
                .orElseThrow(() -> new DdcAdminException(
                        DdcManagementErrorCode.PUBLISH_TASK_NOT_FOUND
                ));
        List<DdcManagementPublishTarget> targets =
                publishAckRepository.findByChangeId(changeId).stream()
                        .sorted(Comparator
                                .comparing(DdcPublishAckBO::getInstanceId)
                                .thenComparing(
                                        DdcPublishAckBO::getLeaseId,
                                        Comparator.nullsFirst(String::compareTo)
                                ))
                        .map(this::target)
                        .toList();
        return new DdcManagementPublishTask(
                task.getChangeId(),
                status(task.getStatus()),
                task.getTargetVersion(),
                task.getResourceChecksum(),
                count(task.getTargetCount()),
                count(task.getAckCount()),
                count(task.getFailedCount()),
                count(task.getIgnoredCount()),
                count(task.getTimeoutCount()),
                count(task.getAttemptCount()),
                targets,
                task.getErrorMessage(),
                instant(task.getCreatedAt()),
                instant(task.getDispatchedAt()),
                instant(task.getCompletedAt())
        );
    }

    public DdcManagementPublishResult retry(String changeId) {
        publishService.retry(changeId);
        return result(getPublishTask(changeId));
    }

    /** 使用可信操作者重试发布任务。 / Retries a publication task with a trusted operator. */
    public DdcManagementPublishResult retry(
            String changeId,
            String operator) {
        publishService.retry(
                requireText(changeId, "changeId"),
                requireText(operator, "operator")
        );
        return result(getPublishTask(changeId));
    }

    /**
     * 查询匹配的作用域绑定并转换为稳定管理模型。
     * / Retrieves matching scope bindings and maps them to the stable management model.
     *
     * @param query 作用域绑定筛选条件 / scope-binding filters
     * @return 匹配的作用域绑定 / matching scope bindings
     */
    public List<DdcManagementScopeBinding> getScopeBindings(
            DdcManagementScopeQuery query) {
        require(query, "scope query");
        return bindingService.list(
                        query.bizCode(),
                        query.namespaceCode(),
                        query.env(),
                        query.appCode()
                ).stream()
                .map(value -> new DdcManagementScopeBinding(
                        value.id(),
                        value.bizCode(),
                        value.namespaceCode(),
                        value.env(),
                        value.appId(),
                        value.appCode(),
                        value.appName(),
                        value.enabled()
                ))
                .toList();
    }

    /** Approved Service inventory plus complete, current observations; no legacy registration reads or cleanup. */
    public DdcManagementServiceCatalog getServiceKeys(DdcManagementServiceQuery query) {
        DdcTenantContextDTO trusted = registryContext();
        List<DdcServiceObservationBindingVO> bindings = matchingBindings(trusted, query);
        List<DdcManagementServiceKey> keys = new ArrayList<>();
        Instant observedAt = null;
        for (var binding : bindings) {
            var view = currentService(trusted, binding);
            keys.add(serviceKey(binding));
            if (observedAt == null || view.observedAt().isBefore(observedAt)) observedAt = view.observedAt();
        }
        // The old Redis generation is retired. Kubernetes resourceVersion is opaque, not a replacement counter.
        return new DdcManagementServiceCatalog(0L, observedAt, List.copyOf(keys));
    }

    public DdcManagementServiceSnapshot getInstances(DdcManagementServiceQuery query) {
        return getInstances(query, null);
    }

    /** bindingId disambiguates independently approved physical Services sharing a logical tuple. */
    public DdcManagementServiceSnapshot getInstances(DdcManagementServiceQuery query, String bindingId) {
        require(query, "service query");
        requireText(query.bizCode(), "bizCode");
        requireText(query.env(), "env");
        requireText(query.appCode(), "appCode");
        requireText(query.serviceKind(), "serviceKind");
        requireText(query.serviceName(), "serviceName");
        requireText(query.protocol(), "protocol");
        if (bindingId != null && (bindingId.isBlank() || bindingId.length() > 128
                || bindingId.chars().anyMatch(Character::isISOControl))) {
            throw new DdcAdminException("invalid bindingId");
        }
        DdcTenantContextDTO trusted = registryContext();
        var candidates = matchingBindings(trusted, query).stream()
                .filter(binding -> bindingId == null || binding.bindingId().equals(bindingId)).toList();
        if (candidates.isEmpty()) throw registryFailure(404, "TIANSHU_SERVICE_BINDING_NOT_FOUND");
        if (candidates.size() != 1) throw registryFailure(409, "TIANSHU_SERVICE_BINDING_AMBIGUOUS");
        var binding = candidates.getFirst();
        var view = currentService(trusted, binding);
        var instances = view.endpoints().stream()
                .sorted(Comparator.comparing(DdcServiceObservationViewVO.EndpointVO::podUid)
                        .thenComparingInt(DdcServiceObservationViewVO.EndpointVO::targetPort))
                .map(endpoint -> serviceInstance(binding, view, endpoint)).toList();
        return new DdcManagementServiceSnapshot(serviceKey(binding), 0L, view.observedAt(), instances);
    }

    private DdcTenantContextDTO registryContext() {
        try {
            Long tenant = EgonColaTenantIdProvider.currentTenantId();
            var registered = tenants.requireEnabledTenant(tenant);
            if (tenant == null || tenant <= 0 || !tenant.equals(registered.getTenantId())) throw registryUnavailable();
            return new DdcTenantContextDTO(tenant, registered.getIdentityTenantId(), MDC.get("userId"));
        } catch (RuntimeException unavailable) {
            throw registryUnavailable();
        }
    }

    private List<DdcServiceObservationBindingVO> matchingBindings(
            DdcTenantContextDTO trusted, DdcManagementServiceQuery query) {
        require(query, "service query");
        String kind = optionalKind(query.serviceKind());
        List<DdcServiceObservationBindingVO> owned;
        try {
            owned = serviceBindings.bindings().stream()
                    .filter(binding -> binding.tenantId() == trusted.tenantId())
                    .toList();
        } catch (RuntimeException unavailable) {
            throw registryUnavailable();
        }
        if (owned.isEmpty() || owned.stream().anyMatch(binding ->
                !trusted.identityTenantId().equals(binding.identityTenantId()))) throw registryUnavailable();
        return owned.stream().filter(binding -> matches(query.bizCode(), binding.bizCode())
                && matches(query.namespaceCode(), binding.namespaceCode())
                && matches(query.env(), binding.env()) && matches(query.appCode(), binding.appCode())
                && matches(kind, serviceKind(binding)) && matches(query.protocol(), binding.protocol())
                && matches(query.serviceName(), binding.logicalServiceName())
                && matches(query.group(), binding.group()) && matches(query.version(), binding.version()))
                .sorted(Comparator.comparing(DdcServiceObservationBindingVO::bindingId)).toList();
    }

    private DdcServiceObservationViewVO currentService(
            DdcTenantContextDTO trusted, DdcServiceObservationBindingVO binding) {
        try {
            scopeGate.assertEnabledFresh(binding.bizCode(), binding.appCode(), binding.env(), binding.namespaceCode());
            var view = serviceObservations.findCurrent(trusted, binding).orElseThrow(this::registryUnavailable);
            Instant now = clock.instant();
            if (!view.fresh() || view.observedAt() == null || view.observedAt().isAfter(now)
                    || view.validUntil() == null || !view.validUntil().isAfter(now)
                    || !view.validUntil().isAfter(view.observedAt()) || view.serviceResourceVersion() == null
                    || view.serviceResourceVersion().isBlank()) throw registryUnavailable();
            return view;
        } catch (RuntimeException unavailable) {
            if (unavailable instanceof CommonException known
                    && DdcErrorStatus.SCOPE_DISABLED.getStatus().equals(known.getStatus())) throw known;
            log.warn("Service observation unavailable: code=TIANSHU_SERVICE_OBSERVATION_UNAVAILABLE");
            throw registryUnavailable();
        }
    }

    private boolean matches(String requested, String actual) {
        return requested == null || requested.isBlank() || requested.equals(actual);
    }

    private String optionalKind(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.toUpperCase(Locale.ROOT);
        if (!List.of("RPC_PROVIDER", "HTTP_PROVIDER", "INTERNAL_GATEWAY").contains(normalized)) {
            throw new DdcAdminException("unsupported serviceKind");
        }
        return normalized;
    }

    private String serviceKind(DdcServiceObservationBindingVO binding) {
        return "grpc".equals(binding.protocol()) ? "RPC_PROVIDER" : "HTTP_PROVIDER";
    }

    private CommonException registryUnavailable() {
        return registryFailure(503, "TIANSHU_SERVICE_OBSERVATION_UNAVAILABLE");
    }

    private CommonException registryFailure(int status, String code) {
        return new CommonException(status, code, code, true, null);
    }

    private DdcBizBO findBizByCode(String bizCode) {
        if (bizCode == null || bizCode.isBlank()) {
            return null;
        }
        try {
            return bizService.findByBizCode(bizCode);
        } catch (CommonException notFound) {
            return null;
        }
    }

    private DdcManagementBiz business(DdcBizBO value) {
        return new DdcManagementBiz(
                value.getPublicId(),
                value.getBizCode(),
                value.getBizName(),
                Boolean.TRUE.equals(value.getEnabled())
        );
    }

    private DdcManagementApp application(
            DdcAppBO value,
            DdcBizBO business) {
        if (value == null || business == null) {
            return null;
        }
        return new DdcManagementApp(
                value.getPublicId(),
                business.getPublicId(),
                business.getBizCode(),
                value.getAppCode(),
                value.getAppName(),
                Boolean.TRUE.equals(value.getEnabled()),
                Boolean.TRUE.equals(business.getEnabled())
        );
    }

    private DdcManagementPublishResult result(DdcManagementPublishTask task) {
        return new DdcManagementPublishResult(
                task.changeId(),
                task.status(),
                task.targetVersion(),
                task.resourceChecksum(),
                task.targetCount(),
                task.targets(),
                task.errorMessage(),
                task.createdAt(),
                task.dispatchedAt(),
                task.completedAt()
        );
    }

    private DdcManagementConfig config(DdcConfigVO value) {
        return new DdcManagementConfig(
                value.getBizCode(),
                value.getEnv(),
                value.getAppCode(),
                value.getResourceName(),
                value.getContent(),
                value.getFormat(),
                value.getCurrentVersion(),
                Boolean.TRUE.equals(value.getEnabled()),
                Boolean.TRUE.equals(value.getDeleted()),
                instant(value.getUpdatedAt())
        );
    }

    private DdcManagementPublishTarget target(DdcPublishAckBO value) {
        return new DdcManagementPublishTarget(
                value.getInstanceId(),
                value.getLeaseId(),
                value.getCurrentVersion(),
                value.getAckStatus() == null ? "PENDING" : value.getAckStatus(),
                value.getErrorMessage(),
                instant(value.getAckAt())
        );
    }

    private DdcManagementServiceKey serviceKey(DdcServiceObservationBindingVO value) {
        return new DdcManagementServiceKey(value.bizCode(), value.env(), value.appCode(), value.bindingId(),
                serviceKind(value), value.logicalServiceName(), value.group(), value.version(), value.protocol());
    }

    private DdcManagementServiceInstance serviceInstance(DdcServiceObservationBindingVO binding,
            DdcServiceObservationViewVO view, DdcServiceObservationViewVO.EndpointVO endpoint) {
        var metadata = new LinkedHashMap<String, String>();
        metadata.put("observationSource", "KUBERNETES");
        metadata.put("bindingId", binding.bindingId());
        metadata.put("bindingRevision", Long.toString(binding.bindingRevision()));
        metadata.put("clusterId", binding.clusterId());
        metadata.put("kubernetesNamespace", binding.kubernetesNamespace());
        metadata.put("serviceName", binding.serviceName());
        metadata.put("serviceUid", binding.serviceUid());
        metadata.put("serviceResourceVersion", view.serviceResourceVersion());
        metadata.put("podUid", endpoint.podUid());
        metadata.put("podName", endpoint.podName());
        metadata.put("observedAt", view.observedAt().toString());
        metadata.put("validUntil", view.validUntil().toString());
        // Absent nullable conditions remain unknown; Map<String,String> cannot carry JSON null.
        putCondition(metadata, "ready", endpoint.ready());
        putCondition(metadata, "serving", endpoint.serving());
        putCondition(metadata, "terminating", endpoint.terminating());
        return new DdcManagementServiceInstance(binding.bindingId() + ":" + endpoint.podUid() + ":" + endpoint.targetPort(),
                null, null, endpoint.targetPort(), false, Map.copyOf(metadata), "UNKNOWN", null, null, null);
    }

    private void putCondition(Map<String, String> metadata, String key, Boolean condition) {
        if (condition != null) metadata.put(key, condition.toString());
    }

    private DdcManagementPublishStatus status(String value) {
        try {
            return DdcManagementPublishStatus.valueOf(value);
        } catch (RuntimeException exception) {
            return DdcManagementPublishStatus.UNKNOWN;
        }
    }

    private void validateScope(
            String bizCode,
            String env,
            String appCode
    ) {
        requireText(bizCode, "bizCode");
        requireText(appCode, "appCode");
        requireText(env, "env");
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new DdcAdminException(fieldName + " is required");
        }
        return value;
    }

    private int count(Integer value) {
        return value == null ? 0 : value;
    }

    private <T> T require(T value, String fieldName) {
        if (Objects.isNull(value)) {
            throw new DdcAdminException(fieldName + " is required");
        }
        return value;
    }

    private Instant instant(LocalDateTime value) {
        return value == null
                ? null
                : value.atZone(ZoneId.systemDefault()).toInstant();
    }

}
