package top.egon.cola.component.tianshu.admin.model.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.Set;
import java.util.regex.Pattern;

/** Approved ownership and exact Kubernetes Service port for one observation binding. */
public record DdcServiceObservationBindingVO(
        @Positive long tenantId, @NotBlank String identityTenantId,
        @NotBlank String bizCode, @NotBlank String namespaceCode, @NotBlank String env, @NotBlank String appCode,
        @NotBlank String bindingId, @Positive long bindingRevision, @NotBlank String clusterId,
        @NotBlank String kubernetesNamespace, @NotBlank String serviceName, @NotBlank String serviceUid,
        @NotBlank String portName, @Positive int servicePort, @NotBlank String protocol,
        @NotBlank String logicalServiceName, String group, String version, Set<String> workloadUids) {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final Pattern DNS_LABEL = Pattern.compile("[a-z0-9](?:[-a-z0-9]{0,61}[a-z0-9])?");

    public DdcServiceObservationBindingVO {
        if (tenantId <= 0 || bindingRevision <= 0 || servicePort < 1 || servicePort > 65_535
                || !IDENTIFIER.matcher(required(identityTenantId)).matches()
                || !IDENTIFIER.matcher(required(bizCode)).matches()
                || !IDENTIFIER.matcher(required(namespaceCode)).matches()
                || !IDENTIFIER.matcher(required(env)).matches()
                || !IDENTIFIER.matcher(required(appCode)).matches()
                || !IDENTIFIER.matcher(required(bindingId)).matches()
                || !IDENTIFIER.matcher(required(clusterId)).matches()
                || !DNS_LABEL.matcher(required(kubernetesNamespace)).matches()
                || !DNS_LABEL.matcher(required(serviceName)).matches()
                || !IDENTIFIER.matcher(required(serviceUid)).matches()
                || !DNS_LABEL.matcher(required(portName)).matches()
                || !("http".equals(protocol) || "grpc".equals(protocol))
                || !IDENTIFIER.matcher(required(logicalServiceName)).matches()
                || !optionalIdentifier(group) || !optionalIdentifier(version)
                || workloadUids == null || workloadUids.isEmpty() || workloadUids.size() > 128
                || workloadUids.stream().anyMatch(uid -> !IDENTIFIER.matcher(required(uid)).matches())) {
            throw invalid();
        }
        group = group == null ? "" : group;
        version = version == null ? "" : version;
        workloadUids = Set.copyOf(workloadUids);
    }

    private static boolean optionalIdentifier(String value) {
        return value == null || value.isEmpty() || IDENTIFIER.matcher(value).matches();
    }

    private static String required(String value) {
        if (value == null) {
            throw invalid();
        }
        return value;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("TIANSHU_SERVICE_OBSERVATION_BINDING_INVALID");
    }
}
