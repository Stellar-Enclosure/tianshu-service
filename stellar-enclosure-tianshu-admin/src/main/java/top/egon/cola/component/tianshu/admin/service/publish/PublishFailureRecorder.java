package top.egon.cola.component.tianshu.admin.service.publish;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import top.egon.cola.component.tianshu.admin.common.DdcAdminException;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO;
import top.egon.cola.component.tianshu.admin.repository.DdcPublishTaskRepository;

import java.util.Optional;

/** Recovers a committed outcome after an ambiguous local commit; it never invents a task. */
@Slf4j
@Service("publishFailureRecorder")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class PublishFailureRecorder {
    @Qualifier("ddcPublishTaskRepository") private final DdcPublishTaskRepository tasks;

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Optional<DdcPublishTaskBO> findCommitted(String changeId) { return tasks.findByChangeId(changeId); }

    /** Legacy preparation errors cannot overwrite committed native or historical business outcomes. */
    public void recordFailure(String changeId, String bizCode, String env, String appCode,
            String resourceName, String errorMessage) {
        throw new DdcAdminException("TIANSHU_LEGACY_PREPARATION_FAILURE_WRITE_RETIRED");
    }
}
