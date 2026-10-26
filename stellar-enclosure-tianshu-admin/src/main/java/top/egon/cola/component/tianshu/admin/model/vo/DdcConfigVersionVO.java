package top.egon.cola.component.tianshu.admin.model.vo;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class DdcConfigVersionVO {

    @PermissionResource(key = "tianshu.field.DdcConfigVersionVO.id", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.id")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String id;
    @PermissionResource(key = "tianshu.field.DdcConfigVersionVO.configId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.configId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String configId;
    @PermissionResource(key = "tianshu.field.DdcConfigVersionVO.version", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.version")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Long version;
    @PermissionResource(key = "tianshu.field.DdcConfigVersionVO.resourceName", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.resourceName")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String resourceName;
    @PermissionResource(key = "tianshu.field.DdcConfigVersionVO.oldContent", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.oldContent")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String oldContent;
    @PermissionResource(key = "tianshu.field.DdcConfigVersionVO.newContent", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.newContent")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String newContent;
    @PermissionResource(key = "tianshu.field.DdcConfigVersionVO.format", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.format")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String format;
    @PermissionResource(key = "tianshu.field.DdcConfigVersionVO.changeType", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.changeType")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String changeType;
    @PermissionResource(key = "tianshu.field.DdcConfigVersionVO.changeReason", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.changeReason")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String changeReason;
    @PermissionResource(key = "tianshu.field.DdcConfigVersionVO.operator", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.operator")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String operator;
    @PermissionResource(key = "tianshu.field.DdcConfigVersionVO.createdAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.createdAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    private LocalDateTime createdAt;
}
