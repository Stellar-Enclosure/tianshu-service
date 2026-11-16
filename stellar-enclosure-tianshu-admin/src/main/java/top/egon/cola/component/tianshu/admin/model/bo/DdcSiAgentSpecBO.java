package top.egon.cola.component.tianshu.admin.model.bo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 原子化 Agent 规格结构化载体（严禁包含静态流程编排字段，编排归口 Tianji Flowable）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
@JsonIgnoreProperties(ignoreUnknown = false)
public class DdcSiAgentSpecBO {

    private String modelKey;
    private RuntimeConfig runtime;
    @Builder.Default
    private List<String> promptKeys = Collections.emptyList();
    @Builder.Default
    private List<String> skillKeys = Collections.emptyList();
    @Builder.Default
    private List<String> mcpServerKeys = Collections.emptyList();
    @Builder.Default
    private List<String> subAgentKeys = Collections.emptyList();
    private Map<String, Object> inputSchema;
    private Map<String, Object> outputSchema;
    private String systemInstruction;
    @Builder.Default
    private List<String> capabilities = Collections.emptyList();
    private Map<String, Object> metadata;

    /** Agent 运行时数值约束参数。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(chain = true)
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = false)
    public static class RuntimeConfig {
        @Builder.Default
        private Integer maxSteps = 8;
        @Builder.Default
        private Integer timeoutSeconds = 60;
        @Builder.Default
        private Double temperature = 0.2d;
        private Integer maxTokens;
    }
}
