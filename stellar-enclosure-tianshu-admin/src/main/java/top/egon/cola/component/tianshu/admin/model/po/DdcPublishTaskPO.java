package top.egon.cola.component.tianshu.admin.model.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.apache.ibatis.type.JdbcType;
import top.egon.cola.component.common.mybatis.model.EgonColaModelValidationGroups;
import top.egon.cola.component.common.mybatis.model.EgonModel;
import top.egon.cola.component.tianshu.admin.dao.typehandler.DdcJsonbTypeHandler;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ddc_publish_task", autoResultMap = true)
public class DdcPublishTaskPO extends EgonModel<DdcPublishTaskPO> {

    @TableField(value = "public_id", updateStrategy = FieldStrategy.NEVER)
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 64, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Update.class,
            EgonColaModelValidationGroups.Persisted.class})
    private String publicId;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 64, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Update.class,
            EgonColaModelValidationGroups.Persisted.class})
    private String changeId;

    @TableField("config_id")
    private Long configItemId;

    @TableField(exist = false)
    private String configId;

    private String bizCode;
    private String appCode;
    private String env;
    private String namespace;

    @TableField("config_key")
    private String resourceName;

    private Long targetVersion;
    private String publishMode;
    @TableField(value = "platform_evidence", jdbcType = JdbcType.OTHER, typeHandler = DdcJsonbTypeHandler.class,
            updateStrategy = FieldStrategy.NEVER)
    private JsonNode platformEvidence;
    private String status;
    private Integer targetCount;
    private Integer ackCount;
    private Integer failedCount;
    private Integer ignoredCount;
    private Integer timeoutCount;
    private Long timeoutMs;
    private String operator;

    @TableField("error_message")
    private String errorMessage;

    @TableField("content_checksum")
    private String resourceChecksum;

    private Integer attemptCount;
    private LocalDateTime dispatchedAt;
    private LocalDateTime completedAt;
    private String failureStage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
