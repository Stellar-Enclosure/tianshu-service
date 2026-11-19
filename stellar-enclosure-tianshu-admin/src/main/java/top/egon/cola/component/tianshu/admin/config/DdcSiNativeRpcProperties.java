package top.egon.cola.component.tianshu.admin.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 部署供应的 SI 权限，不能由请求字段或旧凭据建立绑定。
 * <p>Deployment-only SI privileges; no request field or legacy credential can create a binding.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
@Validated
@ConfigurationProperties(prefix = DdcSiNativeRpcProperties.PREFIX, ignoreUnknownFields = false)
public class DdcSiNativeRpcProperties {
    public static final String PREFIX = "egon.cola.component.tianshu.admin.si.native-rpc";

    @Builder.Default
    @NotNull
    @Size(max = 128)
    private List<@NotNull @Valid BindingDTO> bindings = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(chain = true)
    @Builder
    public static class BindingDTO {
        @NotNull private URI issuer;
        @NotNull private URI resourceUri;
        @NotBlank private String identityTenantId;
        @NotBlank private String clientId;
        @NotBlank private String sourceBizCode;
        @NotBlank private String sourceAppCode;
        @NotBlank private String sourceEnvironment;
        /** 精确原生方法到精确逻辑命名空间，不支持通配符。 <p>Exact native methods and logical namespaces, without wildcards.</p> */
        @Builder.Default
        @NotNull
        @Size(min = 1, max = 4)
        private Map<@NotBlank String, @NotNull @Size(min = 1, max = 64) Set<@NotBlank String>> methods = new LinkedHashMap<>();
    }
}
