package top.egon.cola.component.tianshu.admin.model.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.apache.ibatis.type.JdbcType;
import top.egon.cola.component.common.mybatis.model.EgonModel;
import top.egon.cola.component.tianshu.admin.dao.typehandler.DdcJsonbTypeHandler;
import java.time.Instant;

/** Tenant-owned durable observation; lifecycle and audit fields remain owned by EgonModel. */
@Data @NoArgsConstructor @AllArgsConstructor @Accessors(chain = true)
@SuperBuilder @EqualsAndHashCode(callSuper = true)
@TableName(value = "ddc_config_workload_observation", autoResultMap = true)
public class DdcConfigWorkloadObservationPO extends EgonModel<DdcConfigWorkloadObservationPO> {
    @NotNull @Size(max = 128) private String targetBindingId;
    @NotNull @Positive private Long targetRevision;
    @NotNull @Size(max = 128) private String clusterId;
    @NotNull @Size(max = 63) private String k8sNamespace;
    @NotNull @Size(max = 128) private String workloadUid;
    private Instant observedAt;
    private Instant validUntil;
    @NotNull private Instant recordedAt;
    @NotNull @TableField(value = "view_json", jdbcType = JdbcType.OTHER, typeHandler = DdcJsonbTypeHandler.class)
    private JsonNode viewJson;
}
