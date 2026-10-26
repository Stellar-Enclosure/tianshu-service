package top.egon.cola.component.tianshu.admin.model.bo;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.component.common.mybatis.model.EgonColaModelValidationGroups;

import java.time.Instant;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DdcEnvBO {

    @JsonIgnore private Long id;
    @JsonIgnore private Long tenantId;
    @JsonIgnore private String createUserId;
    @JsonIgnore private Instant createTime;
    @JsonIgnore private String updateUserId;
    @JsonIgnore private Instant updateTime;
    @JsonIgnore private LocalDateTime deletedAt;
    @JsonIgnore private Long version;

    @PermissionResource(key = "tianshu.field.DdcEnvBO.id", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.id")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING)
    @JsonProperty("id")
    @NotNull(groups = EgonColaModelValidationGroups.Persisted.class)
    @Size(max = 64, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Update.class,
            EgonColaModelValidationGroups.Persisted.class})
    private String publicId;

    @PermissionResource(key = "tianshu.field.DdcEnvBO.envCode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.envCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Update.class,
            EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 32, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Update.class,
            EgonColaModelValidationGroups.Persisted.class})
    private String envCode;

    @PermissionResource(key = "tianshu.field.DdcEnvBO.description", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.description")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
    @Size(max = 256, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Update.class,
            EgonColaModelValidationGroups.Persisted.class})
    private String description;

    @PermissionResource(key = "tianshu.field.DdcEnvBO.sortOrder", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.sortOrder")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
    private Integer sortOrder;
    @PermissionResource(key = "tianshu.field.DdcEnvBO.enabled", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.enabled")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN,
            write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
    private Boolean enabled;
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    @PermissionResource(key = "tianshu.field.DdcEnvBO.createdAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.createdAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING)
    private LocalDateTime createdAt;
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    @PermissionResource(key = "tianshu.field.DdcEnvBO.updatedAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.updatedAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING)
    private LocalDateTime updatedAt;
}
