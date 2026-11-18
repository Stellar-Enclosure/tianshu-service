package top.egon.cola.component.tianshu.admin.model.po;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import java.time.Instant;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import top.egon.cola.component.common.mybatis.model.EgonModel;
import top.egon.cola.component.common.mybatis.model.EgonColaModelValidationGroups;
import top.egon.cola.component.tianshu.admin.dao.typehandler.DdcJsonbTypeHandler;
import org.apache.ibatis.type.JdbcType;

/** Permanently unique latest observation for one verified process and fixed target. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ToString(onlyExplicitlyIncluded = true)
@TableName(value = "ddc_config_process_receipt", autoResultMap = true)
public class DdcConfigProcessReceiptPO extends EgonModel<DdcConfigProcessReceiptPO> {
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class}) @Positive(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private Long configId;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class}) @Positive(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private Long configRevision;

    @TableField(value = "scope_json", jdbcType = JdbcType.OTHER, typeHandler = DdcJsonbTypeHandler.class)
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private JsonNode scopeJson;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class}) @Size(max = 128, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class}) @Pattern(regexp = "[\\x21-\\x7e]+", groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String targetBindingId;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class}) @Positive(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private Long targetRevision;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class}) @Size(max = 64, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class}) @Pattern(regexp = "[\\x21-\\x7e]+", groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String clusterId;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class}) @Size(max = 63, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class}) @Pattern(regexp = "[a-z0-9](?:[a-z0-9-]*[a-z0-9])?", groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String k8sNamespace;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class}) @Size(max = 64, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class}) @Pattern(regexp = "[\\x21-\\x7e]+", groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String workloadUid;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class}) @Size(max = 64, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class}) @Pattern(regexp = "[\\x21-\\x7e]+", groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String podUid;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class}) @Size(max = 63, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class}) @Pattern(regexp = "[a-z0-9](?:[a-z0-9-]*[a-z0-9])?", groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String containerName;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class}) @Size(max = 256, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class}) @Pattern(regexp = "[\\x21-\\x7e]+", groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String containerId;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class}) @Size(max = 64, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class}) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}", groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String bootId;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class}) @Pattern(regexp = "[0-9a-f]{64}", groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String contentDigest;

    @Positive(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private Long appliedRevision;

    @Pattern(regexp = "[0-9a-f]{64}", groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String appliedDigest;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class}) @Pattern(regexp = "[0-9a-f]{64}", groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String restartDigest;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class}) @Pattern(regexp = "APPLIED|DISABLED_APPLIED|REJECTED|RESTART_REQUIRED|FAILED", groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String result;

    @Size(max = 64, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class}) @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String reasonCode;

    @Size(max = 1024, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String reasonMessage;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class}) @Positive(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private Long reportSeq;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class}) @Pattern(regexp = "[0-9a-f]{64}", groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String receiptDigest;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private Instant reportedAt;

    private Instant appliedAt;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private Instant lastReceivedAt;

    @Override
    @jakarta.validation.constraints.Null(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    public java.time.LocalDateTime getDeletedAt() { return super.getDeletedAt(); }

}
