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

/** 标准 MCP Server 定义载体（支持 STDIO / SSE / STREAMABLE_HTTP 与凭据引用脱敏）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
@JsonIgnoreProperties(ignoreUnknown = false)
public class DdcSiMcpSpecBO {

    private String protocolVersion;
    private String transport;
    private String endpoint;
    private String command;
    @Builder.Default
    private List<String> args = Collections.emptyList();
    @Builder.Default
    private Map<String, String> env = Collections.emptyMap();
    @Builder.Default
    private Map<String, String> headers = Collections.emptyMap();
    private String credentialRef;
    @Builder.Default
    private List<McpToolItem> tools = Collections.emptyList();
    private Integer timeoutSeconds;

    /** MCP 暴露工具摘要项。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(chain = true)
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class McpToolItem {
        private String name;
        private String description;
        private Map<String, Object> inputSchema;
    }
}
