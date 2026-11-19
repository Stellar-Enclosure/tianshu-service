package top.egon.cola.component.tianshu.admin.service.management;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import top.egon.cola.component.tianshu.admin.support.DdcAdminPageSupport;
import top.egon.cola.component.tianshu.model.management.DdcManagementServiceInstance;
import top.egon.cola.component.tianshu.model.management.DdcManagementServiceKey;
import top.egon.cola.component.tianshu.model.management.DdcManagementServiceQuery;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service("ddcRegistryAdminPageService")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcRegistryAdminPageService {

    private static final Comparator<String> TEXT =
            Comparator.nullsFirst(String::compareTo);

    private static final Comparator<DdcManagementServiceKey> SERVICE_ORDER =
            Comparator.comparing(DdcManagementServiceKey::bizCode, TEXT)
                    .thenComparing(DdcManagementServiceKey::env, TEXT)
                    .thenComparing(DdcManagementServiceKey::appCode, TEXT)
                    .thenComparing(DdcManagementServiceKey::serviceKind, TEXT)
                    .thenComparing(DdcManagementServiceKey::protocol, TEXT)
                    .thenComparing(DdcManagementServiceKey::serviceName, TEXT)
                    .thenComparing(DdcManagementServiceKey::group, TEXT)
                    .thenComparing(DdcManagementServiceKey::version, TEXT)
                    .thenComparing(DdcManagementServiceKey::serviceId, TEXT);

    private static final Comparator<DdcManagementServiceInstance>
            INSTANCE_ORDER = Comparator.comparing(
                    DdcManagementServiceInstance::status, TEXT
            ).thenComparing(
                    DdcManagementServiceInstance::host, TEXT
            ).thenComparingInt(
                    DdcManagementServiceInstance::port
            ).thenComparing(
                    DdcManagementServiceInstance::instanceId, TEXT
            );

    @Qualifier("ddcManagementFacade") private final DdcManagementFacade facade;

    public DdcPageResult<DdcManagementServiceKey> pageServices(
            DdcManagementServiceQuery query,
            PageQuery pageQuery
    ) {
        List<DdcManagementServiceKey> records = facade.getServiceKeys(query)
                .services().stream().sorted(SERVICE_ORDER).toList();
        return DdcAdminPageSupport.slice(records, pageQuery);
    }

    public DdcPageResult<DdcManagementServiceInstance> pageInstances(
            DdcManagementServiceQuery query,
            PageQuery pageQuery
    ) {
        return pageInstances(query, pageQuery, null);
    }

    public DdcPageResult<DdcManagementServiceInstance> pageInstances(
            DdcManagementServiceQuery query, PageQuery pageQuery, String bindingId) {
        var snapshot = bindingId == null ? facade.getInstances(query) : facade.getInstances(query, bindingId);
        List<DdcManagementServiceInstance> records = snapshot.instances().stream().sorted(INSTANCE_ORDER).toList();
        return DdcAdminPageSupport.slice(records, pageQuery);
    }
}
