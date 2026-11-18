package top.egon.cola.component.tianshu.configdata;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.boot.context.config.ConfigDataResource;
import org.springframework.lang.Nullable;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;

/** Required local baseline and independently frozen deployment identity, never a remote transport. */
@Getter
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public final class DdcConfigDataResource extends ConfigDataResource {

    @EqualsAndHashCode.Include
    private final Path baselineFile;
    private final DdcConfigScopeVO expectedScope;
    @EqualsAndHashCode.Include
    private final ExpectedTargetVO expectedTarget;

    public DdcConfigDataResource(Path baselineFile, DdcConfigScopeVO expectedScope, ExpectedTargetVO expectedTarget) {
        super(false);
        if (baselineFile == null || !baselineFile.isAbsolute() || !baselineFile.normalize().equals(baselineFile)
                || baselineFile.getParent() == null || !"baseline.json".equals(baselineFile.getFileName().toString())) {
            throw new IllegalArgumentException("CONFIG_BASELINE_PATH_INVALID");
        }
        this.baselineFile = baselineFile;
        this.expectedScope = Objects.requireNonNull(expectedScope, "CONFIG_BASELINE_SCOPE_REQUIRED");
        this.expectedTarget = Objects.requireNonNull(expectedTarget, "CONFIG_BASELINE_TARGET_REQUIRED");
    }

    @EqualsAndHashCode.Include
    private List<String> scopeIdentity() {
        return List.of(expectedScope.getTenantId(), expectedScope.getBizCode(), expectedScope.getEnv(),
                expectedScope.getAppCode(), expectedScope.getResourceName());
    }

    @Override
    public String toString() {
        return "Tianshu required local baseline";
    }

    public record ExpectedTargetVO(String bindingId, long bindingRevision, String clusterId,
            String kubernetesNamespace, String workloadUid, String containerName) {
        public ExpectedTargetVO {
            token(bindingId, 128);
            token(clusterId, 64);
            token(workloadUid, 64);
            dns(kubernetesNamespace);
            dns(containerName);
            if (bindingRevision <= 0) { throw new IllegalArgumentException("CONFIG_BASELINE_TARGET_INVALID"); }
        }

        private static void token(String value, int maximum) {
            if (value == null || value.isEmpty() || value.length() > maximum
                    || !value.chars().allMatch(character -> character >= 0x21 && character <= 0x7e)) {
                throw new IllegalArgumentException("CONFIG_BASELINE_TARGET_INVALID");
            }
        }

        private static void dns(String value) {
            if (value == null || value.length() > 63 || !value.matches("[a-z0-9]([a-z0-9-]*[a-z0-9])?")) {
                throw new IllegalArgumentException("CONFIG_BASELINE_TARGET_INVALID");
            }
        }
    }

    /** Frozen compatibility/rule metadata; the active HOT values live only in the dynamic property source. */
    public record BaselineMetadataVO(DdcConfigScopeVO scope, DdcConfigBaselineVO.SourceVO source, boolean enabled,
            String contentSha256, String restartDigest, List<DdcConfigBaselineVO.RuleVO> rules,
            DdcConfigBaselineVO.TargetBindingVO targetBinding, @Nullable String dependencySnapshotId) {
        public BaselineMetadataVO {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(contentSha256, "contentSha256");
            Objects.requireNonNull(restartDigest, "restartDigest");
            Objects.requireNonNull(targetBinding, "targetBinding");
            rules = List.copyOf(rules);
        }
    }
}
