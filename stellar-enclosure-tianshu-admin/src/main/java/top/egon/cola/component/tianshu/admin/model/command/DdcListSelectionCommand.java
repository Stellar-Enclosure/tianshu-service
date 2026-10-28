package top.egon.cola.component.tianshu.admin.model.command;

import jakarta.validation.constraints.NotBlank;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

public record DdcListSelectionCommand(
        @PermissionResource(key = "tianshu.field.DdcListSelectionCommand.templateId", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.selectedTemplateId")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, write = @PermissionGroup(codes = "TIANSHU_READ")) @NotBlank String templateId,
        @PermissionResource(key = "tianshu.field.DdcListSelectionCommand.catalogVersion", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.catalogActivationVersion")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, write = @PermissionGroup(codes = "TIANSHU_READ")) @NotBlank String catalogVersion,
        @PermissionResource(key = "tianshu.field.DdcListSelectionCommand.definitionDigest", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.definitionDigest")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, write = @PermissionGroup(codes = "TIANSHU_READ")) @NotBlank String definitionDigest) { }
