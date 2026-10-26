package top.egon.cola.component.tianshu.model.ai;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionMode;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Immutable four-dimensional business scope; the authenticated tenant is deliberately absent. */
public record DdcAiScopeDTO(
        @PermissionResource(key = "tianshu.field.DdcAiScopeDTO.bizCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.bizCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"),
                write = @PermissionGroup(mode = PermissionMode.ANY, codes = {"TIANSHU_AI_PUBLISH", "TIANSHU_AI_WRITE"}))
        @NotBlank @Size(max = 128) @Pattern(regexp = "^[^\\p{Cntrl}]+$") String bizCode,
        @PermissionResource(key = "tianshu.field.DdcAiScopeDTO.namespaceCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.namespaceCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"),
                write = @PermissionGroup(mode = PermissionMode.ANY, codes = {"TIANSHU_AI_PUBLISH", "TIANSHU_AI_WRITE"}))
        @NotBlank @Size(max = 128) @Pattern(regexp = "^[^\\p{Cntrl}]+$") String namespaceCode,
        @PermissionResource(key = "tianshu.field.DdcAiScopeDTO.env", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.env")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"),
                write = @PermissionGroup(mode = PermissionMode.ANY, codes = {"TIANSHU_AI_PUBLISH", "TIANSHU_AI_WRITE"}))
        @NotBlank @Size(max = 32) @Pattern(regexp = "^[^\\p{Cntrl}]+$") String env,
        @PermissionResource(key = "tianshu.field.DdcAiScopeDTO.appCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.appCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"),
                write = @PermissionGroup(mode = PermissionMode.ANY, codes = {"TIANSHU_AI_PUBLISH", "TIANSHU_AI_WRITE"}))
        @NotBlank @Size(max = 128) @Pattern(regexp = "^[^\\p{Cntrl}]+$") String appCode) {

    public DdcAiScopeDTO {
        bizCode = trimIdentity(bizCode);
        namespaceCode = trimIdentity(namespaceCode);
        env = trimIdentity(env);
        appCode = trimIdentity(appCode);
    }

    private static String trimIdentity(String value) {
        return value == null ? null : value.trim();
    }
}
