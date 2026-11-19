package top.egon.cola.component.tianshu.admin.model.bo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;

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

    private String schemaVersion;
    private SourceTypeEnum sourceType;
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
    private String capabilityFingerprint;
    private ServiceBindingVO serviceBinding;
    private ArtifactReferenceVO artifact;
    private ApiMappingReferenceVO apiMapping;
    @Builder.Default
    private List<McpToolItem> tools = Collections.emptyList();
    @Builder.Default
    private List<DependencyVO> dependencies = Collections.emptyList();
    @Builder.Default
    private List<ResourceVO> resources = Collections.emptyList();
    @Builder.Default
    private List<AppVO> apps = Collections.emptyList();
    @Builder.Default
    private List<RemoteMountVO> remoteMounts = Collections.emptyList();
    private Integer timeoutSeconds;

    public enum SourceTypeEnum { INTERNAL_SERVICE, REMOTE_HTTPS, STDIO_ARTIFACT, COMPOSED_API }

    public enum ToolRiskLevelEnum { LOW, MEDIUM, HIGH, CRITICAL }

    public record ServiceBindingVO(String bindingId, String revision, String serviceName, String namespace, int port) { }

    public record ArtifactReferenceVO(String reference, String digestSha256) { }

    public record ApiMappingReferenceVO(String gatewayGroupId, String publicationId, String schemaVersion,
                                        String contentSha256, String artifactSha256) { }

    public record DependencyVO(DdcSiKindEnum kind, String key, String resourceId, String versionId,
                               String versionNo, String digestSha256, String ownerAppCode) { }

    public record ResourceVO(String uri, String name, String mimeType, String description, String digestSha256) { }

    public record AppVO(String appKey, String resourceUri, ArtifactReferenceVO artifact) { }

    public record RemoteMountVO(String mountId, String providerKey, String namespacePrefix, String capabilityFingerprint) { }

    /** MCP 暴露工具摘要项。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(chain = true)
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class McpToolItem {
        private String toolId;
        private String name;
        private String description;
        private Map<String, Object> inputSchema;
        private Map<String, Object> outputSchema;
        private List<String> requiredPermissions;
        private ToolRiskLevelEnum riskLevel;
        private Boolean idempotent;
        private Boolean enabled;
    }
}
