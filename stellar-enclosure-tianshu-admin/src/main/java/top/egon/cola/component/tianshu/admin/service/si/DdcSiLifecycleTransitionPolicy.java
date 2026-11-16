package top.egon.cola.component.tianshu.admin.service.si;

import org.springframework.stereotype.Component;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** SI 资源生命周期状态合法迁移矩阵与跨环境晋级白名单策略。 */
@Component("ddcSiLifecycleTransitionPolicy")
public class DdcSiLifecycleTransitionPolicy {

    private static final int CODE_CONFLICT = 56409;
    private static final int CODE_VALIDATION = 56422;

    private static final Map<DdcSiLifecycleStatusEnum, Set<DdcSiLifecycleStatusEnum>> ALLOWED_TRANSITIONS = Map.of(
            DdcSiLifecycleStatusEnum.DRAFT, Set.of(DdcSiLifecycleStatusEnum.DRAFT, DdcSiLifecycleStatusEnum.PUBLISHED, DdcSiLifecycleStatusEnum.ARCHIVED),
            DdcSiLifecycleStatusEnum.PUBLISHED, Set.of(DdcSiLifecycleStatusEnum.PUBLISHED, DdcSiLifecycleStatusEnum.DEPRECATED, DdcSiLifecycleStatusEnum.ARCHIVED),
            DdcSiLifecycleStatusEnum.DEPRECATED, Set.of(DdcSiLifecycleStatusEnum.PUBLISHED, DdcSiLifecycleStatusEnum.ARCHIVED),
            DdcSiLifecycleStatusEnum.ARCHIVED, Set.of());

    private final Map<String, List<String>> promotionRoutes;

    public DdcSiLifecycleTransitionPolicy() {
        this(Map.of(
                "dev", List.of("test", "staging"),
                "test", List.of("staging", "prod"),
                "staging", List.of("prod")));
    }

    public DdcSiLifecycleTransitionPolicy(Map<String, List<String>> promotionRoutes) {
        this.promotionRoutes = promotionRoutes == null ? Map.of() : Map.copyOf(promotionRoutes);
    }

    public void assertCanEditDraft(DdcSiLifecycleStatusEnum current) {
        if (current == DdcSiLifecycleStatusEnum.ARCHIVED) {
            throw new CommonException(CODE_CONFLICT, "TIANSHU_SI_ARCHIVED_IMMUTABLE",
                    "archived SI resource cannot be modified");
        }
    }

    public void assertCanTransition(DdcSiLifecycleStatusEnum current, DdcSiLifecycleStatusEnum target) {
        if (current == DdcSiLifecycleStatusEnum.ARCHIVED) {
            throw new CommonException(CODE_CONFLICT, "TIANSHU_SI_ARCHIVED_IMMUTABLE",
                    "archived SI resource is immutable");
        }
        Set<DdcSiLifecycleStatusEnum> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new CommonException(CODE_CONFLICT, "TIANSHU_SI_LIFECYCLE_CONFLICT",
                    "illegal lifecycle transition from " + current + " to " + target);
        }
    }

    public void assertCanPromote(String sourceEnv, String targetEnv) {
        if (sourceEnv == null || targetEnv == null || sourceEnv.equals(targetEnv)) {
            throw new CommonException(CODE_VALIDATION, "TIANSHU_SI_PROMOTION_INVALID",
                    "promotion requires distinct non-blank sourceEnv and targetEnv");
        }
        List<String> targets = promotionRoutes.getOrDefault(sourceEnv, List.of());
        if (!targets.contains(targetEnv)) {
            throw new CommonException(CODE_VALIDATION, "TIANSHU_SI_PROMOTION_FORBIDDEN",
                    "promotion not permitted from " + sourceEnv + " to " + targetEnv);
        }
    }
}
