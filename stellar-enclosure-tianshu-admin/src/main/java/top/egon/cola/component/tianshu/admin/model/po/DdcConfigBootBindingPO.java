package top.egon.cola.component.tianshu.admin.model.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import top.egon.cola.component.common.mybatis.model.EgonModel;

/** Immutable fixed-zero container incarnation binding; the positive row ID is its durable fence. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ddc_config_boot_binding_t0", schema = "public", autoResultMap = true)
public class DdcConfigBootBindingPO extends EgonModel<DdcConfigBootBindingPO> {

    @TableField(value = "cluster_id", updateStrategy = FieldStrategy.NEVER)
    @NotBlank @Size(max = 64) @Pattern(regexp = "[!-~]+")
    private String clusterId;

    @TableField(value = "pod_uid", updateStrategy = FieldStrategy.NEVER)
    @NotBlank @Size(max = 64) @Pattern(regexp = "[!-~]+")
    private String podUid;

    @TableField(value = "container_name", updateStrategy = FieldStrategy.NEVER)
    @NotBlank @Size(max = 63) @Pattern(regexp = "[a-z0-9]([a-z0-9-]*[a-z0-9])?")
    private String containerName;

    @TableField(value = "container_id", updateStrategy = FieldStrategy.NEVER)
    @NotBlank @Size(max = 256) @Pattern(regexp = "[!-~]+")
    private String containerId;

    @TableField(value = "boot_id", updateStrategy = FieldStrategy.NEVER)
    @NotBlank @Size(max = 64) @Pattern(regexp = "[!-~]+")
    private String bootId;

    @TableField(value = "workload_uid", updateStrategy = FieldStrategy.NEVER)
    @NotBlank @Size(max = 64) @Pattern(regexp = "[!-~]+")
    private String workloadUid;

    @TableField(value = "k8s_namespace", updateStrategy = FieldStrategy.NEVER)
    @NotBlank @Size(max = 63) @Pattern(regexp = "[a-z0-9]([a-z0-9-]*[a-z0-9])?")
    private String k8sNamespace;
}
