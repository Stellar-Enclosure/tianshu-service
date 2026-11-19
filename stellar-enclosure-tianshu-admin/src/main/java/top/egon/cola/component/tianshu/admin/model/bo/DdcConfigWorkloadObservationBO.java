package top.egon.cola.component.tianshu.admin.model.bo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import java.time.Instant;
import java.time.LocalDateTime;

/** Internal storage projection. The repository exposes typed workload views instead of this JSON envelope. */
@Data @NoArgsConstructor @AllArgsConstructor @Accessors(chain = true) @Builder
public class DdcConfigWorkloadObservationBO {
    private Long id;
    private Long tenantId;
    private String createUserId;
    private Instant createTime;
    private String updateUserId;
    private Instant updateTime;
    private LocalDateTime deletedAt;
    private Long version;
    private String targetBindingId;
    private Long targetRevision;
    private String clusterId;
    private String k8sNamespace;
    private String workloadUid;
    private Instant observedAt;
    private Instant validUntil;
    private Instant recordedAt;
    private JsonNode viewJson;
}
