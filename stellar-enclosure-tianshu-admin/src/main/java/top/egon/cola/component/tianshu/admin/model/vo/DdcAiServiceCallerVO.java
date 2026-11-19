package top.egon.cola.component.tianshu.admin.model.vo;

import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantContextDTO;
import top.egon.cola.component.tianshu.api.ai.DdcAiHttpContract.Operation;
import top.egon.cola.component.tianshu.model.ai.DdcAiScopeDTO;

import java.util.Set;

/** Deployment-bound SERVICE caller; credential rotation does not change its durable audit identity. */
public record DdcAiServiceCallerVO(DdcTenantContextDTO tenant, String stableActorId,
        String sourceBizCode, String sourceAppCode, String sourceEnvironment,
        Set<DdcAiScopeDTO> placements, Set<Operation> operations) {
    public DdcAiServiceCallerVO {
        placements = Set.copyOf(placements);
        operations = Set.copyOf(operations);
    }
}
