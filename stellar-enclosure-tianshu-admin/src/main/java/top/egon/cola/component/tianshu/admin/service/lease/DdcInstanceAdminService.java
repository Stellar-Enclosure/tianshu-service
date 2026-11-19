package top.egon.cola.component.tianshu.admin.service.lease;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesObservationService.StatusEnum;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesObservationService.WorkloadViewVO;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcInstanceResultConverter;
import top.egon.cola.component.tianshu.admin.model.vo.DdcInstanceResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import top.egon.cola.component.tianshu.admin.repository.DdcInstanceRepository;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.admin.service.publish.DdcConfigManifestFactory.PublicationPolicyResolver;
import top.egon.cola.component.tianshu.admin.service.publish.DdcPublishTaskQueryService.WorkloadObservationReader;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO.TargetBindingVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Current instances are authorized Kubernetes facts; old instance rows are immutable historical telemetry. */
@Slf4j
@Validated
@Service("ddcInstanceAdminService")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcInstanceAdminService {
    private static final int MAX_TARGETS = 32;
    @Qualifier("ddcInstanceRepository") private final DdcInstanceRepository history;
    @Qualifier("ddcInstanceResultConverterImpl") private final DdcInstanceResultConverter resultConverter;
    @Qualifier("ddcConfigPublicationPolicyResolver") private final PublicationPolicyResolver policies;
    @Qualifier("ddcWorkloadObservationReader") private final ObjectProvider<WorkloadObservationReader> observations;
    @Qualifier("ddcTenantRegistry") private final DdcTenantRegistry tenants;
    @Qualifier("ddcPublishClock") private final Clock clock;

    /** Does not consult legacy instance/lease rows, including when current evidence is unavailable. */
    public List<WorkloadViewVO> list(@NotBlank String bizCode, @NotBlank String env, @NotBlank String appCode) {
        DdcTenantContextDTO trusted = trustedContext();
        var scope = new DdcConfigScopeVO(trusted.tenantId().toString(), bizCode, env, appCode, "application.yml");
        var policy = policies.require(trusted, scope);
        if (policy == null || !scope.equals(policy.scope()) || policy.targetBindings().isEmpty()
                || policy.targetBindings().size() > MAX_TARGETS) throw invalidAuthority();
        var unique = new HashSet<String>();
        for (var target : policy.targetBindings()) {
            if (target == null || !unique.add(target.getBindingId())) throw invalidAuthority();
        }
        WorkloadObservationReader reader;
        try { reader = observations.getIfAvailable(); }
        catch (RuntimeException unavailable) { reader = null; }
        var result = new ArrayList<WorkloadViewVO>();
        for (var target : policy.targetBindings()) result.add(observe(target, reader, clock.instant()));
        result.sort(Comparator.comparing((WorkloadViewVO view) -> view.target().getClusterId())
                .thenComparing(view -> view.target().getKubernetesNamespace())
                .thenComparing(view -> view.target().getWorkloadUid())
                .thenComparing(view -> view.target().getBindingId()));
        return List.copyOf(result);
    }

    public DdcPageResult<WorkloadViewVO> page(@NotBlank String bizCode, @NotBlank String env,
            @NotBlank String appCode, PageQuery query) {
        PageQuery page = query == null ? PageQuery.defaultPage() : query;
        List<WorkloadViewVO> all = list(bizCode, env, appCode);
        int start = Math.min(all.size(), page.offset());
        int end = (int) Math.min((long) all.size(), (long) start + page.pageSize());
        return DdcPageResult.<WorkloadViewVO>builder().records(all.subList(start, end)).total(all.size())
                .pageNo(page.pageNo()).pageSize(page.pageSize()).build();
    }

    /** Stored status and heartbeat are historical observations; they establish no current availability. */
    public List<DdcInstanceResult> historyList(@NotBlank String bizCode, @NotBlank String env, @NotBlank String appCode) {
        trustedContext();
        return resultConverter.toTargetList(history.findHistoricalByScope(bizCode, env, appCode));
    }

    public DdcPageResult<DdcInstanceResult> historyPage(@NotBlank String bizCode, @NotBlank String env,
            @NotBlank String appCode, PageQuery query) {
        trustedContext();
        PageQuery page = query == null ? PageQuery.defaultPage() : query;
        var stored = history.findHistoricalByScope(bizCode, env, appCode, page);
        return DdcPageResult.<DdcInstanceResult>builder().records(resultConverter.toTargetList(stored.getRecords()))
                .total(stored.getTotal()).pageNo(stored.getPageNo()).pageSize(stored.getPageSize()).build();
    }

    private DdcTenantContextDTO trustedContext() {
        Long tenant = EgonColaTenantIdProvider.currentTenantId();
        var registered = tenants.requireEnabledTenant(tenant);
        if (!Objects.equals(tenant, registered.getTenantId())) throw invalidAuthority();
        return new DdcTenantContextDTO(tenant, registered.getIdentityTenantId(), MDC.get("userId"));
    }

    private WorkloadViewVO observe(TargetBindingVO target, WorkloadObservationReader reader, Instant now) {
        if (reader == null) return unknown(target, StatusEnum.UNAVAILABLE, "CONFIG_CURRENT_EVIDENCE_UNAVAILABLE", null, null, now);
        final WorkloadViewVO view;
        try { view = reader.currentView(target); }
        catch (RuntimeException unavailable) {
            return unknown(target, StatusEnum.UNAVAILABLE, "CONFIG_CURRENT_EVIDENCE_UNAVAILABLE", null, null, now);
        }
        if (view == null || view.status() == null || !target.equals(view.target())) {
            return unknown(target, StatusEnum.UNAVAILABLE, "CONFIG_CURRENT_EVIDENCE_INVALID", null, null, now);
        }
        if (view.status() != StatusEnum.FRESH) {
            return unknown(target, view.status(), view.reasonCode(), view.observedAt(), view.validUntil(), view.invalidatedAt());
        }
        if (!target.getWorkloadUid().equals(view.workloadUid())) {
            return unknown(target, StatusEnum.IDENTITY_CONFLICT, "WORKLOAD_UID_MISMATCH", view.observedAt(), view.validUntil(), now);
        }
        if (view.observedAt() == null || view.validUntil() == null || view.observedAt().isAfter(now)
                || !view.validUntil().isAfter(view.observedAt())) {
            return unknown(target, StatusEnum.UNAVAILABLE, "CONFIG_CURRENT_EVIDENCE_INVALID", null, null, now);
        }
        if (!now.isBefore(view.validUntil())) {
            return unknown(target, StatusEnum.STALE, "OBSERVATION_EXPIRED", view.observedAt(), view.validUntil(), view.validUntil());
        }
        return view;
    }

    private static WorkloadViewVO unknown(TargetBindingVO target, StatusEnum status, String reason,
            Instant observed, Instant expiry, Instant invalidated) {
        return new WorkloadViewVO(target, status, reason, observed, expiry, invalidated, target.getWorkloadUid(),
                0, 0, 0, List.of(), List.of(), Map.of(), Map.of());
    }

    private static IllegalStateException invalidAuthority() {
        return new IllegalStateException("TIANSHU_CURRENT_INSTANCE_SCOPE_UNVERIFIED");
    }
}
