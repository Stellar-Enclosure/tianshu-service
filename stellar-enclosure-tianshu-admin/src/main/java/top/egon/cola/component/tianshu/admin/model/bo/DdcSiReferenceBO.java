package top.egon.cola.component.tianshu.admin.model.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiReferenceTargetTypeEnum;

import java.time.Instant;
import java.time.LocalDateTime;

/** SI 资源与流程有向依赖引用业务对象。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DdcSiReferenceBO {

    private Long id;
    private Long tenantId;
    private String createUserId;
    private Instant createTime;
    private String updateUserId;
    private Instant updateTime;
    private LocalDateTime deletedAt;
    private Long version;

    private Long sourceResourceId;
    private Long sourceVersionNo;
    private DdcSiReferenceTargetTypeEnum sourceType;
    private String sourceKey;
    private DdcSiReferenceTargetTypeEnum targetType;
    private DdcSiKindEnum targetKind;
    private String targetKey;
    private Long targetVersionNo;
    private Long pinnedVersionNo;
    private String envCode;
    private Long namespaceId;
    @Builder.Default
    private boolean required = true;
    private String roleHint;
}
