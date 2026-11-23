package top.egon.cola.component.tianshu.admin.service.audit;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Fixed, dimension-free audit counters. Observability never changes command or acknowledgement results. */
@Slf4j
@Component("ddcOperationLogMetrics")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcOperationLogMetrics {

    // Discover the host's optional primary/composite registry without imposing a new registry bean name.
    private final ObjectProvider<MeterRegistry> registries;

    public void accepted() { increment("tianshu.operation.log.audit.accepted"); }
    public void committed() { increment("tianshu.operation.log.audit.committed"); }
    public void duplicateSame() { increment("tianshu.operation.log.duplicate.same"); }
    public void duplicateConflict() { increment("tianshu.operation.log.duplicate.conflict"); }
    public void queryUnavailable() { increment("tianshu.operation.log.query.unavailable"); }
    public void partitionMissing() { increment("tianshu.operation.log.partition.missing"); }

    private void increment(String name) {
        try {
            MeterRegistry registry = registries.getIfAvailable();
            if (registry != null) {
                registry.counter(name).increment();
            }
        } catch (RuntimeException unavailable) {
            log.debug("Operation log metric recording unavailable");
        }
    }
}
