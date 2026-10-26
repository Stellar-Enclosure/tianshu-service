package top.egon.cola.component.tianshu.admin.model.vo;

import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.FieldPermissions;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionGroup;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.component.tianshu.admin.model.dto.DdcAiResourceSummaryDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiContentDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DdcAiResourceResult {

    @PermissionResource(key = "tianshu.field.DdcAiResourceResult.resource", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.resource")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @Valid
    private DdcAiResourceSummaryDTO resource;

    @PermissionResource(key = "tianshu.field.DdcAiResourceResult.draft", type = ResourceType.FIELD,
            labelPage = "tianshu.permissions", labelKey = "field.draft")
    @FieldPermissions(valueType = PermissionManifest.JsonFieldType.OBJECT,
            plainRead = @PermissionGroup(codes = "TIANSHU_AI_READ"))
    @NotNull
    @Valid
    private DdcAiContentDTO draft;
}
