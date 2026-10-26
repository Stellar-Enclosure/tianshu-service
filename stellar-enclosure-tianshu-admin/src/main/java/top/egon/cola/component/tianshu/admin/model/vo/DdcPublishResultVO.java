package top.egon.cola.component.tianshu.admin.model.vo;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import lombok.Getter;
import lombok.Setter;
import top.egon.cola.component.tianshu.admin.model.bo.DdcPublishTaskBO;

@Getter
@Setter
public class DdcPublishResultVO {

    @PermissionResource(key = "tianshu.field.DdcPublishResultVO.changeId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.changeId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String changeId;

    @PermissionResource(key = "tianshu.field.DdcPublishResultVO.status", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.status")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String status;

    @PermissionResource(key = "tianshu.field.DdcPublishResultVO.targetCount", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.targetCount")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Integer targetCount;

    @PermissionResource(key = "tianshu.field.DdcPublishResultVO.ackCount", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.ackCount")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Integer ackCount;

    @PermissionResource(key = "tianshu.field.DdcPublishResultVO.failedCount", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.failedCount")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Integer failedCount;

    @PermissionResource(key = "tianshu.field.DdcPublishResultVO.ignoredCount", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.ignoredCount")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Integer ignoredCount;

    @PermissionResource(key = "tianshu.field.DdcPublishResultVO.timeoutCount", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.timeoutCount")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Integer timeoutCount;

    @PermissionResource(key = "tianshu.field.DdcPublishResultVO.attemptCount", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.attemptCount")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Integer attemptCount;

    @PermissionResource(key = "tianshu.field.DdcPublishResultVO.targetVersion", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.targetVersion")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Long targetVersion;

    @PermissionResource(key = "tianshu.field.DdcPublishResultVO.resourceChecksum", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.resourceChecksum")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String resourceChecksum;

    @PermissionResource(key = "tianshu.field.DdcPublishResultVO.errorMessage", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.errorMessage")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String errorMessage;

    public static DdcPublishResultVO from(DdcPublishTaskBO task) {
        DdcPublishResultVO vo = new DdcPublishResultVO();
        vo.setChangeId(task.getChangeId());
        vo.setStatus(task.getStatus());
        vo.setTargetCount(task.getTargetCount());
        vo.setAckCount(task.getAckCount());
        vo.setFailedCount(task.getFailedCount());
        vo.setIgnoredCount(task.getIgnoredCount());
        vo.setTimeoutCount(task.getTimeoutCount());
        vo.setAttemptCount(task.getAttemptCount());
        vo.setTargetVersion(task.getTargetVersion());
        vo.setResourceChecksum(task.getResourceChecksum());
        vo.setErrorMessage(task.getErrorMessage());
        return vo;
    }
}
