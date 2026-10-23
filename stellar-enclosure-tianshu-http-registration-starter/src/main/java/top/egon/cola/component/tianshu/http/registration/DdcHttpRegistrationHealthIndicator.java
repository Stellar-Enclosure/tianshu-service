package top.egon.cola.component.tianshu.http.registration;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import top.egon.cola.component.tianshu.model.lease.DdcLeaseSession;

public final class DdcHttpRegistrationHealthIndicator
        implements HealthIndicator {

    private final DdcHttpRegistrationRuntime runtime;

    public DdcHttpRegistrationHealthIndicator(
            DdcHttpRegistrationRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public Health health() {
        DdcHttpRegistrationState state = runtime.state();
        Health.Builder builder = switch (state) {
            case REGISTERED -> Health.up();
            case RECOVERING -> Health.outOfService();
            case FAILED, STOPPED -> Health.down();
            default -> Health.unknown();
        };
        builder.withDetail("state", state.name())
                .withDetail("instanceId", runtime.instanceId());
        runtime.lease().ifPresent(current -> addLeaseDetails(
                builder,
                current
        ));
        return builder.build();
    }

    private void addLeaseDetails(
            Health.Builder builder,
            DdcLeaseSession lease) {
        builder.withDetail("leaseId", lease.leaseId())
                .withDetail("leaseExpireAt", lease.leaseExpireAt());
    }
}
