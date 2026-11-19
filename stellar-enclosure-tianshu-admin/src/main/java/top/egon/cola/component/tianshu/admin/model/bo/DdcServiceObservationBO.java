package top.egon.cola.component.tianshu.admin.model.bo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.LocalDateTime;

/** Internal persistence projection; callers receive the typed Service view only. */
@Data @NoArgsConstructor @AllArgsConstructor @Accessors(chain = true) @Builder
public class DdcServiceObservationBO {
    private Long id;
    private Long tenantId;
    private String createUserId;
    private Instant createTime;
    private String updateUserId;
    private Instant updateTime;
    private LocalDateTime deletedAt;
    private Long version;
    private String identityTenantId;
    private String bindingId;
    private Long bindingRevision;
    private String bindingFingerprint;
    private String clusterId;
    private String k8sNamespace;
    private String serviceName;
    private String serviceUid;
    private String portName;
    private Integer servicePort;
    private Long writerEpoch;
    private Long reportSeq;
    private Instant observedAt;
    private Instant validUntil;
    private Instant recordedAt;
    private JsonNode viewJson;
}
