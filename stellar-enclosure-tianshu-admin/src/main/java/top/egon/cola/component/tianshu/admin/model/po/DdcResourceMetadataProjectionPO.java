package top.egon.cola.component.tianshu.admin.model.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import top.egon.cola.component.common.mybatis.model.EgonColaModelValidationGroups;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.Accessors;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import top.egon.cola.component.common.mybatis.model.EgonModel;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ddc_resource_metadata_projection", autoResultMap = true)
public class DdcResourceMetadataProjectionPO extends EgonModel<DdcResourceMetadataProjectionPO> {
    @TableField(value = "catalog_scope_id", updateStrategy = FieldStrategy.NEVER)
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Positive(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private Long catalogScopeId;

    @TableField(value = "source_id", updateStrategy = FieldStrategy.NEVER)
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Positive(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private Long sourceId;

    @TableField(value = "producer_id", updateStrategy = FieldStrategy.NEVER)
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 128, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String producerId;

    @TableField(value = "source_version", updateStrategy = FieldStrategy.NEVER)
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 64, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String sourceVersion;

    @TableField(value = "metadata_kind", updateStrategy = FieldStrategy.NEVER)
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 16, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String metadataKind;

    @TableField(value = "definition_key", updateStrategy = FieldStrategy.NEVER)
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 200, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String definitionKey;

    @TableField(value = "resource_id", updateStrategy = FieldStrategy.NEVER)
    @Positive(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private Long resourceId;

    @TableField(value = "permission_id", updateStrategy = FieldStrategy.NEVER)
    @Positive(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private Long permissionId;

    @TableField(value = "resource_type", updateStrategy = FieldStrategy.NEVER)
    @Size(max = 16, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String resourceType;

    @TableField(value = "definition_lifecycle", updateStrategy = FieldStrategy.NEVER)
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 16, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String definitionLifecycle;

    @TableField(value = "page_code", updateStrategy = FieldStrategy.NEVER)
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 128, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String pageCode;

    @TableField(value = "label_key", updateStrategy = FieldStrategy.NEVER)
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 128, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String labelKey;

    @TableField(value = "description_key", updateStrategy = FieldStrategy.NEVER)
    @Size(max = 128, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String descriptionKey;

    @TableField(value = "target_json", updateStrategy = FieldStrategy.NEVER)
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String targetJson;

    @TableField(value = "policy_json", updateStrategy = FieldStrategy.NEVER)
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String policyJson;

    @TableField(value = "i18n_refs_json", updateStrategy = FieldStrategy.NEVER)
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String i18nRefsJson;

    @TableField(value = "referenced_permission_codes_json", updateStrategy = FieldStrategy.NEVER)
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String referencedPermissionCodesJson;
}
