package top.egon.cola.component.tianshu.admin.model.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.component.tianshu.model.si.DdcSiBindingStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;

import java.time.Instant;
import java.time.LocalDateTime;

/** SI 应用服务版本锁定绑定业务对象。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DdcSiBindingBO {

    private Long id;
    private Long tenantId;
    private String createUserId;
    private Instant createTime;
    private String updateUserId;
    private Instant updateTime;
    private LocalDateTime deletedAt;
    private Long version;

    private String bizCode;
    private Long namespaceId;
    private String envCode;
    private String appCode;
    private DdcSiKindEnum kind;
    private Long resourceId;
    private String resourceKey;
    private Long pinnedVersionNo;
    private DdcSiBindingStatusEnum status;
    private Instant boundTime;
}
