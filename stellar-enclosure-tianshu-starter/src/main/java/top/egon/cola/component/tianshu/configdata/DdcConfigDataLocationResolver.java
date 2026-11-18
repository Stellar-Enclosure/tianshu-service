package top.egon.cola.component.tianshu.configdata;

import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationResolver;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;

/** Resolves only the deployment-pinned local baseline import; it creates no network or credential client. */
public final class DdcConfigDataLocationResolver implements ConfigDataLocationResolver<DdcConfigDataResource> {

    public static final String PREFIX = "tianshu-baseline:";
    public static final String SETTINGS_PREFIX = "egon.cola.component.tianshu.baseline";
    private static final String DEFAULT_FILE = "/var/run/tianshu/config/baseline.json";

    @Override
    public boolean isResolvable(ConfigDataLocationResolverContext context, ConfigDataLocation location) {
        return location != null && location.hasPrefix(PREFIX);
    }

    @Override
    public List<DdcConfigDataResource> resolve(ConfigDataLocationResolverContext context, ConfigDataLocation location) {
        if (location.isOptional()) { throw new IllegalArgumentException("CONFIG_BASELINE_OPTIONAL_FORBIDDEN"); }
        String raw = location.getNonPrefixedValue(PREFIX);
        if (raw == null || !raw.startsWith("/") || raw.startsWith("//") || raw.contains(":")
                || raw.contains("?") || raw.contains("#")) {
            throw new IllegalArgumentException("CONFIG_BASELINE_PATH_INVALID");
        }
        Path path = Path.of(raw);
        String configured = context.getBinder().bind(SETTINGS_PREFIX + ".file", String.class).orElse(DEFAULT_FILE);
        if (!path.equals(Path.of(configured)) || !raw.equals(path.toString())) {
            throw new IllegalArgumentException("CONFIG_BASELINE_PATH_MISMATCH");
        }
        DdcConfigScopeVO scope = new DdcConfigScopeVO(required(context, "scope.tenant-id"), required(context, "scope.biz-code"),
                required(context, "scope.env"), required(context, "scope.app-code"), required(context, "scope.resource-name"));
        String revision = required(context, "target.binding-revision");
        if (!revision.matches("[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("CONFIG_BASELINE_TARGET_INVALID");
        }
        long bindingRevision;
        try { bindingRevision = Long.parseLong(revision); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("CONFIG_BASELINE_TARGET_INVALID"); }
        var target = new DdcConfigDataResource.ExpectedTargetVO(required(context, "target.binding-id"), bindingRevision,
                required(context, "target.cluster-id"), required(context, "target.kubernetes-namespace"),
                required(context, "target.workload-uid"), required(context, "target.container-name"));
        return List.of(new DdcConfigDataResource(path, scope, target));
    }

    private static String required(ConfigDataLocationResolverContext context, String suffix) {
        return context.getBinder().bind(SETTINGS_PREFIX + "." + suffix, String.class)
                .orElseThrow(() -> new IllegalArgumentException("CONFIG_BASELINE_STARTUP_IDENTITY_REQUIRED"));
    }
}
