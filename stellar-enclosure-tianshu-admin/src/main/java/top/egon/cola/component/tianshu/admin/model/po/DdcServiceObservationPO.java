package top.egon.cola.component.tianshu.admin.model.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
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

/** Independent tenant-owned Service observation; lifecycle/audit columns remain EgonModel-owned. */
@Data @NoArgsConstructor @AllArgsConstructor @Accessors(chain = true)
@SuperBuilder @EqualsAndHashCode(callSuper = true)
@TableName(value = "ddc_service_observation", autoResultMap = true)
public class DdcServiceObservationPO extends EgonModel<DdcServiceObservationPO> {
    @NotNull @Size(max = 128) private String identityTenantId;
    @NotNull @Size(max = 128) private String bindingId;
    @NotNull @Positive private Long bindingRevision;
    @NotNull @Pattern(regexp = "sha256:[0-9a-f]{64}") private String bindingFingerprint;
    @NotNull @Size(max = 128) private String clusterId;
    @NotNull @Size(max = 63) private String k8sNamespace;
    @NotNull @Size(max = 63) private String serviceName;
    @NotNull @Size(max = 128) private String serviceUid;
    @NotNull @Size(max = 63) private String portName;
    @NotNull @Min(1) @Max(65535) private Integer servicePort;
    @NotNull @Positive private Long writerEpoch;
    @NotNull @Positive private Long reportSeq;
    private Instant observedAt;
    private Instant validUntil;
    @NotNull private Instant recordedAt;
    @NotNull @TableField(value = "view_json", jdbcType = JdbcType.OTHER, typeHandler = DdcJsonbTypeHandler.class) private JsonNode viewJson;
}
