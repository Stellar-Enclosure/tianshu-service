package top.egon.cola.component.tianshu.admin.model.bo;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.ActivationDescriptor;
import java.util.List;

/** Immutable local projection identity; it is never an active pointer or an authorization decision. */
public record DdcCatalogSelectionBO(Long projectionId, ActivationDescriptor descriptor, List<Member> members) {
    public DdcCatalogSelectionBO { members = List.copyOf(members); }
    public record Member(String producerId, String producerVersion, String sourceVersion, Long sourceId) { }
}
