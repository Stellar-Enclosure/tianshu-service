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
public class DdcAppBO {

    @JsonIgnore private Long id;
    @JsonIgnore private Long tenantId;
    @JsonIgnore private String createUserId;
    @JsonIgnore private Instant createTime;
    @JsonIgnore private String updateUserId;
    @JsonIgnore private Instant updateTime;
    @JsonIgnore private LocalDateTime deletedAt;
    @JsonIgnore private Long version;

    @PermissionResource(key = "tianshu.field.DdcAppBO.id", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.id")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING)
    @JsonProperty("id")
    @NotNull(groups = EgonColaModelValidationGroups.Persisted.class)
    @Size(max = 64, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Update.class,
            EgonColaModelValidationGroups.Persisted.class})
    private String publicId;

    @PermissionResource(key = "tianshu.field.DdcAppBO.appCode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.appCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Update.class,
            EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 128, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Update.class,
            EgonColaModelValidationGroups.Persisted.class})
    private String appCode;

    @PermissionResource(key = "tianshu.field.DdcAppBO.appName", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.appName")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Update.class,
            EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 128, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Update.class,
            EgonColaModelValidationGroups.Persisted.class})
    private String appName;

    @PermissionResource(key = "tianshu.field.DdcAppBO.owner", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.owner")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
    @Size(max = 128, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Update.class,
            EgonColaModelValidationGroups.Persisted.class})
    private String owner;

    @PermissionResource(key = "tianshu.field.DdcAppBO.description", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.description")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
    @Size(max = 512, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Update.class,
            EgonColaModelValidationGroups.Persisted.class})
    private String description;

    @PermissionResource(key = "tianshu.field.DdcAppBO.enabled", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.enabled")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN,
            write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
    private Boolean enabled;
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    @PermissionResource(key = "tianshu.field.DdcAppBO.createdAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.createdAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING)
    private LocalDateTime createdAt;
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    @PermissionResource(key = "tianshu.field.DdcAppBO.updatedAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.updatedAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING)
    private LocalDateTime updatedAt;

    @PermissionResource(key = "tianshu.field.DdcAppBO.bizCode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.bizCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            write = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Update.class,
            EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 128, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Update.class,
            EgonColaModelValidationGroups.Persisted.class})
    private String bizCode;
}
