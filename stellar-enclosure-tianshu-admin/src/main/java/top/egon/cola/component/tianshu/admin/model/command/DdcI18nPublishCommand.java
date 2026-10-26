package top.egon.cola.component.tianshu.admin.model.command;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record DdcI18nPublishCommand(
        @PermissionResource(key = "tianshu.field.DdcI18nPublishCommand.expectedRevision", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.expectedRevision")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                write = @PermissionGroup(codes = {"TIANSHU_PUBLISH"}))
        @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$") String expectedRevision,
        @PermissionResource(key = "tianshu.field.DdcI18nPublishCommand.operationId", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.operationId")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                write = @PermissionGroup(codes = {"TIANSHU_PUBLISH"}))
        @NotBlank @Size(max = 36) String operationId) {
    public DdcI18nPublishCommand(String expectedRevision) { this(expectedRevision, null); }
}
