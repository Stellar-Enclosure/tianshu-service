package top.egon.cola.component.tianshu.admin.model.vo;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import java.time.Instant;
import java.util.Map;

public record DdcI18nPublishResult(
        @PermissionResource(key = "tianshu.field.DdcI18nPublishResult.pageId", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.pageId")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String pageId,
        @PermissionResource(key = "tianshu.field.DdcI18nPublishResult.operationId", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.operationId")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String operationId,
        @PermissionResource(key = "tianshu.field.DdcI18nPublishResult.publicationRevision", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.publicationRevision")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String publicationRevision,
        @PermissionResource(key = "tianshu.field.DdcI18nPublishResult.draftRevision", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.draftRevision")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String draftRevision,
        @PermissionResource(key = "tianshu.field.DdcI18nPublishResult.basedOnActivationVersion", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.basedOnActivationVersion")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        String basedOnActivationVersion,
        @PermissionResource(key = "tianshu.field.DdcI18nPublishResult.publishedAt", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.publishedAt")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.STRING,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
        Instant publishedAt,
        @PermissionResource(key = "tianshu.field.DdcI18nPublishResult.translations", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.translations")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        Map<String, Map<String, String>> translations,
        @PermissionResource(key = "tianshu.field.DdcI18nPublishResult.reviewedSourceHashes", type = ResourceType.FIELD,
                labelPage = "tianshu.permissions", labelKey = "field.reviewedSourceHashes")
        @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
                plainRead = @PermissionGroup(codes = "TIANSHU_READ"))
        Map<String, String> reviewedSourceHashes) { }
