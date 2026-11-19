package top.egon.cola.component.tianshu.admin.model.vo;

import com.fasterxml.jackson.annotation.JsonValue;
import top.egon.cola.component.tianshu.model.config.DdcConfigBaselineVO.ReloadModeEnum;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import java.util.List;

/** Non-secret metadata for a candidate snapshot. The preview performs no writes. */
public record DdcConfigPreviewVO(
        @PermissionResource(key = "tianshu.field.DdcConfigPreviewVO.publishedRevision", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.publishedRevision")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
        String publishedRevision,
        @PermissionResource(key = "tianshu.field.DdcConfigPreviewVO.rolloutRequired", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.rolloutRequired")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN,
                plainRead = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
        boolean rolloutRequired,
        @PermissionResource(key = "tianshu.field.DdcConfigPreviewVO.changes", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.configChanges")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.ARRAY,
                plainRead = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
        List<ConfigChangeVO> changes,
        @PermissionResource(key = "tianshu.field.DdcConfigPreviewVO.secretReferences", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.secretReferences")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.ARRAY,
                plainRead = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
        List<ConfigSecretReferenceVO> secretReferences,
        @PermissionResource(key = "tianshu.field.DdcConfigPreviewVO.targets", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.targets")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.ARRAY,
                plainRead = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
        List<ConfigTargetVO> targets) {

    public DdcConfigPreviewVO {
        changes = List.copyOf(changes);
        secretReferences = List.copyOf(secretReferences);
        targets = List.copyOf(targets);
    }

    public record ConfigChangeVO(
            @PermissionResource(key = "tianshu.field.DdcConfigPreviewVO.ConfigChangeVO.key", type = ResourceType.FIELD,
                    labelPage = "tianshu.permissions", labelKey = "field.configKey")
            @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                    plainRead = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
            String key,
            @PermissionResource(key = "tianshu.field.DdcConfigPreviewVO.ConfigChangeVO.mode", type = ResourceType.FIELD,
                    labelPage = "tianshu.permissions", labelKey = "field.reloadMode")
            @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                    plainRead = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
            ReloadModeEnum mode,
            @PermissionResource(key = "tianshu.field.DdcConfigPreviewVO.ConfigChangeVO.change", type = ResourceType.FIELD,
                    labelPage = "tianshu.permissions", labelKey = "field.changeType")
            @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                    plainRead = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
            ConfigChangeEnum change) { }

    public record ConfigSecretReferenceVO(
            @PermissionResource(key = "tianshu.field.DdcConfigPreviewVO.ConfigSecretReferenceVO.key", type = ResourceType.FIELD,
                    labelPage = "tianshu.permissions", labelKey = "field.configKey")
            @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                    plainRead = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
            String key,
            @PermissionResource(key = "tianshu.field.DdcConfigPreviewVO.ConfigSecretReferenceVO.name", type = ResourceType.FIELD,
                    labelPage = "tianshu.permissions", labelKey = "field.secretReferenceName")
            @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                    plainRead = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
            String name,
            @PermissionResource(key = "tianshu.field.DdcConfigPreviewVO.ConfigSecretReferenceVO.version", type = ResourceType.FIELD,
                    labelPage = "tianshu.permissions", labelKey = "field.secretReferenceVersion")
            @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                    plainRead = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
            String version) { }

    public record ConfigTargetVO(
            @PermissionResource(key = "tianshu.field.DdcConfigPreviewVO.ConfigTargetVO.bindingId", type = ResourceType.FIELD,
                    labelPage = "tianshu.permissions", labelKey = "field.targetBindingId")
            @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                    plainRead = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
            String bindingId,
            @PermissionResource(key = "tianshu.field.DdcConfigPreviewVO.ConfigTargetVO.clusterId", type = ResourceType.FIELD,
                    labelPage = "tianshu.permissions", labelKey = "field.clusterId")
            @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                    plainRead = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
            String clusterId,
            @PermissionResource(key = "tianshu.field.DdcConfigPreviewVO.ConfigTargetVO.namespace", type = ResourceType.FIELD,
                    labelPage = "tianshu.permissions", labelKey = "field.k8sNamespace")
            @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                    plainRead = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
            String namespace,
            @PermissionResource(key = "tianshu.field.DdcConfigPreviewVO.ConfigTargetVO.workloadName", type = ResourceType.FIELD,
                    labelPage = "tianshu.permissions", labelKey = "field.workloadName")
            @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                    plainRead = @PermissionGroup(codes = {"TIANSHU_WRITE"}))
            String workloadName) { }

    public enum ConfigChangeEnum {
        ADDED, REMOVED, UPDATED, RULE_CHANGED;

        @JsonValue
        public String wireValue() { return name(); }
    }
}
