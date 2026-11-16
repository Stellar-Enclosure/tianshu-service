package top.egon.cola.component.tianshu.admin.model.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;

import java.time.Instant;
import java.time.LocalDateTime;

/** SI 不可变版本快照及跨环境晋级溯源业务对象。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DdcSiVersionBO {

    private Long id;
    private Long tenantId;
    private String createUserId;
    private Instant createTime;
    private String updateUserId;
    private Instant updateTime;
    private LocalDateTime deletedAt;
    private Long version;

    private Long resourceId;
    private Long versionNo;
    private DdcSiKindEnum kind;
    private String specJson;
    private String digestSha256;
    private String sourceEnvCode;
    private Long sourceVersionNo;
    @Builder.Default
    private String changeNote = "";
    @Builder.Default
    private String indexStatus = "PENDING";
    private Instant publishedTime;
    private Instant indexedTime;
    private String envCode;
}
