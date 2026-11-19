package top.egon.cola.component.tianshu.admin.model.convertor;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import top.egon.cola.component.common.core.converter.BaseForwardConverter;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesObservationService.PodObservationVO;
import top.egon.cola.component.tianshu.admin.kubernetes.DdcKubernetesObservationService.WorkloadViewVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcInstanceWorkloadVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcInstanceWorkloadVO.PodVO;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DdcInstanceWorkloadConverter extends BaseForwardConverter<WorkloadViewVO, DdcInstanceWorkloadVO> {

    @Override
    @Mapping(target = "targetBindingId", source = "target.bindingId")
    @Mapping(target = "clusterId", source = "target.clusterId")
    @Mapping(target = "k8sNamespace", source = "target.kubernetesNamespace")
    @Mapping(target = "desiredReplicas", expression = "java(view.fresh() ? view.desiredReplicas() : null)")
    @Mapping(target = "pods", source = "currentPods")
    DdcInstanceWorkloadVO toTarget(WorkloadViewVO view);

    PodVO toPod(PodObservationVO observation);
}
