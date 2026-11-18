package top.egon.cola.component.tianshu.admin.model.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.LocalDateTime;

/** Authorized incarnation input or the exact committed binding projection; id is the boot fence. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DdcConfigBootBindingBO {

    private Long id;
    private Long tenantId;
    private String createUserId;
    private Instant createTime;
    private String updateUserId;
    private Instant updateTime;
    private LocalDateTime deletedAt;
    private Long version;

    @NotBlank @Size(max = 64) @Pattern(regexp = "[!-~]+")
    private String clusterId;

    @NotBlank @Size(max = 64) @Pattern(regexp = "[!-~]+")
    private String podUid;

    @NotBlank @Size(max = 63) @Pattern(regexp = "[a-z0-9]([a-z0-9-]*[a-z0-9])?")
    private String containerName;

    @NotBlank @Size(max = 256) @Pattern(regexp = "[!-~]+")
    private String containerId;

    @NotBlank @Size(max = 64) @Pattern(regexp = "[!-~]+")
    private String bootId;

    @NotBlank @Size(max = 64) @Pattern(regexp = "[!-~]+")
    private String workloadUid;

    @NotBlank @Size(max = 63) @Pattern(regexp = "[a-z0-9]([a-z0-9-]*[a-z0-9])?")
    private String k8sNamespace;
}
