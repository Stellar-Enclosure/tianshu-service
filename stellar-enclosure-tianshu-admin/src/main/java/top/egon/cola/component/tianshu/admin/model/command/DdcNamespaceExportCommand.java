package top.egon.cola.component.tianshu.admin.model.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import top.egon.cola.component.tianshu.admin.permission.DdcPermissionCodes;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

public record DdcNamespaceExportCommand(
        @PermissionResource(key = "tianshu.field.DdcNamespaceExportCommand.bizCode", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.bizCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, write = @PermissionGroup(codes = DdcPermissionCodes.NAMESPACE_EXPORT)) @Size(max = 128) String bizCode,
        @PermissionResource(key = "tianshu.field.DdcNamespaceExportCommand.keyword", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.keyword")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, write = @PermissionGroup(codes = DdcPermissionCodes.NAMESPACE_EXPORT)) @Size(max = 128) String keyword,
        @PermissionResource(key = "tianshu.field.DdcNamespaceExportCommand.range", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.exportRange")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, write = @PermissionGroup(codes = DdcPermissionCodes.NAMESPACE_EXPORT)) @NotBlank String range,
        @PermissionResource(key = "tianshu.field.DdcNamespaceExportCommand.pageNo", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.pageNo")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER, write = @PermissionGroup(codes = DdcPermissionCodes.NAMESPACE_EXPORT)) int pageNo,
        @PermissionResource(key = "tianshu.field.DdcNamespaceExportCommand.pageSize", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.pageSize")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER, write = @PermissionGroup(codes = DdcPermissionCodes.NAMESPACE_EXPORT)) int pageSize,
        @PermissionResource(key = "tianshu.field.DdcNamespaceExportCommand.locale", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.locale")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, write = @PermissionGroup(codes = DdcPermissionCodes.NAMESPACE_EXPORT)) @NotBlank String locale,
        @PermissionResource(key = "tianshu.field.DdcNamespaceExportCommand.catalogVersion", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.catalogActivationVersion")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, write = @PermissionGroup(codes = DdcPermissionCodes.NAMESPACE_EXPORT)) @NotBlank String catalogVersion,
        @PermissionResource(key = "tianshu.field.DdcNamespaceExportCommand.definitionDigest", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.definitionDigest")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, write = @PermissionGroup(codes = DdcPermissionCodes.NAMESPACE_EXPORT)) @NotBlank String definitionDigest,
        @PermissionResource(key = "tianshu.field.DdcNamespaceExportCommand.templateId", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.selectedTemplateId")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, write = @PermissionGroup(codes = DdcPermissionCodes.NAMESPACE_EXPORT)) String templateId,
        @PermissionResource(key = "tianshu.field.DdcNamespaceExportCommand.templateVersion", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.version")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, write = @PermissionGroup(codes = DdcPermissionCodes.NAMESPACE_EXPORT)) String templateVersion) {
    public DdcNamespaceExportCommand {
        if (range == null) range = "FILTERED";
    }
}
