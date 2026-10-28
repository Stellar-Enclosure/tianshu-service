package top.egon.cola.component.tianshu.admin.model.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import top.egon.cola.component.tianshu.admin.permission.DdcPermissionCodes;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionMode;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import java.util.List;

public record DdcListTemplateCommand(
        @PermissionResource(key = "tianshu.field.DdcListTemplateCommand.id", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.id")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, write = @PermissionGroup(mode = PermissionMode.ANY, codes = {DdcPermissionCodes.NAMESPACE_TEMPLATE_SYSTEM, DdcPermissionCodes.NAMESPACE_TEMPLATE_PERSONAL})) String id,
        @PermissionResource(key = "tianshu.field.DdcListTemplateCommand.expectedVersion", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.expectedRevision")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, write = @PermissionGroup(mode = PermissionMode.ANY, codes = {DdcPermissionCodes.NAMESPACE_TEMPLATE_SYSTEM, DdcPermissionCodes.NAMESPACE_TEMPLATE_PERSONAL})) String expectedVersion,
        @PermissionResource(key = "tianshu.field.DdcListTemplateCommand.name", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.name")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, write = @PermissionGroup(mode = PermissionMode.ANY, codes = {DdcPermissionCodes.NAMESPACE_TEMPLATE_SYSTEM, DdcPermissionCodes.NAMESPACE_TEMPLATE_PERSONAL})) @NotBlank @Size(max = 128) String name,
        @PermissionResource(key = "tianshu.field.DdcListTemplateCommand.columnKeys", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.columnKeys")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.ARRAY, write = @PermissionGroup(mode = PermissionMode.ANY, codes = {DdcPermissionCodes.NAMESPACE_TEMPLATE_SYSTEM, DdcPermissionCodes.NAMESPACE_TEMPLATE_PERSONAL})) @NotNull @Size(max = 256) List<String> columnKeys,
        @PermissionResource(key = "tianshu.field.DdcListTemplateCommand.defaultTemplate", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.defaultTemplate")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN, write = @PermissionGroup(mode = PermissionMode.ANY, codes = {DdcPermissionCodes.NAMESPACE_TEMPLATE_SYSTEM, DdcPermissionCodes.NAMESPACE_TEMPLATE_PERSONAL})) boolean defaultTemplate,
        @PermissionResource(key = "tianshu.field.DdcListTemplateCommand.catalogVersion", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.catalogActivationVersion")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, write = @PermissionGroup(mode = PermissionMode.ANY, codes = {DdcPermissionCodes.NAMESPACE_TEMPLATE_SYSTEM, DdcPermissionCodes.NAMESPACE_TEMPLATE_PERSONAL})) @NotBlank String catalogVersion,
        @PermissionResource(key = "tianshu.field.DdcListTemplateCommand.definitionDigest", type = ResourceType.FIELD, labelPage = "tianshu.permissions", labelKey = "field.definitionDigest")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING, write = @PermissionGroup(mode = PermissionMode.ANY, codes = {DdcPermissionCodes.NAMESPACE_TEMPLATE_SYSTEM, DdcPermissionCodes.NAMESPACE_TEMPLATE_PERSONAL})) @NotBlank String definitionDigest) { }
