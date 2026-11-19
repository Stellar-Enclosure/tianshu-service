package top.egon.cola.component.tianshu.admin.model.vo;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DdcPublishTaskResult {

    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.id", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.id")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    @JsonProperty("id")
    @NotNull
    private String publicId;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.changeId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.changeId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String changeId;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.configId", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.configId")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String configId;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.bizCode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.bizCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String bizCode;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.appCode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.appCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String appCode;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.env", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.env")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String env;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.namespace", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.namespace")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String namespace;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.resourceName", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.resourceName")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String resourceName;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.targetVersion", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.targetVersion")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Long targetVersion;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.publishMode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.publishMode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String publishMode;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.resourceChecksum", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.resourceChecksum")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String resourceChecksum;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.attemptCount", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.attemptCount")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Integer attemptCount;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.dispatchedAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.dispatchedAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    private LocalDateTime dispatchedAt;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.completedAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.completedAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    private LocalDateTime completedAt;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.failureStage", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.failureStage")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String failureStage;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.status", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.status")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String status;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.targetCount", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.targetCount")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Integer targetCount;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.ackCount", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.ackCount")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Integer ackCount;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.failedCount", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.failedCount")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Integer failedCount;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.ignoredCount", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.ignoredCount")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Integer ignoredCount;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.timeoutCount", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.timeoutCount")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Integer timeoutCount;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.timeoutMs", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.timeoutMs")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Long timeoutMs;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.operator", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.operator")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String operator;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.errorMessage", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.errorMessage")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String errorMessage;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.createdAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.createdAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    private LocalDateTime createdAt;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.updatedAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.updatedAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    private LocalDateTime updatedAt;

    /** Current evidence is additive; legacy ACK counters above retain archive semantics. */
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.desiredRevision", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.desiredRevision")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String desiredRevision;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.historyOnly", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.historyOnly")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private Boolean historyOnly;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.platformState", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.platformState")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String platformState;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.rolloutState", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.rolloutState")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String rolloutState;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.processState", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.processState")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String processState;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.observedAt", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.observedAt")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING,
            pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", timezone = "UTC")
    private Instant observedAt;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.observationValidUntil", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.observationValidUntil")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING,
            pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", timezone = "UTC")
    private Instant observationValidUntil;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.reasonCode", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.reasonCode")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private String reasonCode;
    @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.targets", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.targets")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.ARRAY,
            plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
    private List<TargetResult> targets;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(chain = true)
    @Builder
    public static class TargetResult {
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.TargetResult.targetBindingId", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.targetBindingId")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private String targetBindingId;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.TargetResult.targetRevision", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.targetRevision")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private String targetRevision;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.TargetResult.clusterId", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.clusterId")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private String clusterId;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.TargetResult.k8sNamespace", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.k8sNamespace")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private String k8sNamespace;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.TargetResult.workloadUid", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.workloadUid")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private String workloadUid;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.TargetResult.desiredRevision", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.desiredRevision")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private String desiredRevision;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.TargetResult.workloadGeneration", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.workloadGeneration")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private String workloadGeneration;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.TargetResult.observedGeneration", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.observedGeneration")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private String observedGeneration;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.TargetResult.desiredReplicas", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.desiredReplicas")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private Integer desiredReplicas;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.TargetResult.currentCount", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.currentCount")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private Integer currentCount;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.TargetResult.appliedCount", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.appliedCount")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private Integer appliedCount;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.TargetResult.failedCount", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.failedCount")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private Integer failedCount;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.TargetResult.waitingCount", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.waitingCount")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private Integer waitingCount;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.TargetResult.platformState", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.platformState")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private String platformState;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.TargetResult.rolloutState", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.rolloutState")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private String rolloutState;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.TargetResult.processState", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.processState")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private String processState;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.TargetResult.observedAt", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.observedAt")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING,
                pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", timezone = "UTC")
        private Instant observedAt;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.TargetResult.reasonCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.reasonCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private String reasonCode;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.TargetResult.currentProcesses", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.currentProcesses")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.ARRAY,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private List<ProcessResult> currentProcesses;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(chain = true)
    @Builder
    public static class ProcessResult {
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.ProcessResult.podUid", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.podUid")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private String podUid;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.ProcessResult.podName", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.podName")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private String podName;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.ProcessResult.containerName", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.containerName")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private String containerName;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.ProcessResult.containerId", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.containerId")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private String containerId;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.ProcessResult.bootId", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.bootId")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private String bootId;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.ProcessResult.ready", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.ready")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.BOOLEAN,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private Boolean ready;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.ProcessResult.restartCount", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.restartCount")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.NUMBER,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private Integer restartCount;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.ProcessResult.startedAt", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.startedAt")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING,
                pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", timezone = "UTC")
        private Instant startedAt;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.ProcessResult.appliedRevision", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.appliedRevision")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private String appliedRevision;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.ProcessResult.reportSeq", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.reportSeq")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private String reportSeq;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.ProcessResult.status", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.status")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private String status;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.ProcessResult.errorCode", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.errorCode")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        private String errorCode;
        @PermissionResource(key = "tianshu.field.DdcPublishTaskResult.ProcessResult.lastReportedAt", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.lastReportedAt")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING,
                pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", timezone = "UTC")
        private Instant lastReportedAt;
    }
}
