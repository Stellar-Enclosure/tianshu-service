package top.egon.cola.component.tianshu.admin.model.bo;

import lombok.Data;
import java.time.Instant;

@Data
public class DdcListDefinitionBO {
    private Long id;
    private Long tenantId;
    private Long catalogScopeId;
    private Long activationVersion;
    private String producerId;
    private Long producerVersion;
    private String artifactDigest;
    private String pageResourceKey;
    private String listKey;
    private String definitionDigest;
    private String definitionJson;
    private Instant createdAt;
}
