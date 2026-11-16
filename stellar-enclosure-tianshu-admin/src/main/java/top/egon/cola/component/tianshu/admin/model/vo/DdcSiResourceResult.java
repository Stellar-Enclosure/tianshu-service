package top.egon.cola.component.tianshu.admin.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiSourceTypeEnum;

import java.time.Instant;
import java.util.List;

/** Resource detail and catalog summary VO for SI governance API responses. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "Super-Intelligence resource response view")
public class DdcSiResourceResult {

    @Schema(description = "Resource primary identifier")
    private Long id;

    @Schema(description = "Business domain code")
    private String bizCode;

    @Schema(description = "Namespace identifier")
    private Long namespaceId;

    @Schema(description = "Namespace code")
    private String namespaceCode;

    @Schema(description = "Environment code")
    private String envCode;

    @Schema(description = "Owning application code")
    private String appCode;

    @Schema(description = "SI resource kind")
    private DdcSiKindEnum kind;

    @Schema(description = "Resource key")
    private String resourceKey;

    @Schema(description = "Display name")
    private String displayName;

    @Schema(description = "Description")
    private String description;

    @Schema(description = "Discovery tags")
    private List<String> tags;

    @Schema(description = "Lifecycle status")
    private DdcSiLifecycleStatusEnum lifecycleStatus;

    @Schema(description = "Registration source type")
    private DdcSiSourceTypeEnum sourceType;

    @Schema(description = "Current draft specification JSON (sanitized)")
    private String specJson;

    @Schema(description = "Draft specification SHA-256 hex digest")
    private String contentSha256;

    @Schema(description = "Latest allocated immutable version number")
    private Long lastVersionNo;

    @Schema(description = "Currently active version identifier")
    private Long currentVersionId;

    @Schema(description = "Currently active version number")
    private Long currentVersionNo;

    @Schema(description = "Optimistic lock revision")
    private Long version;

    @Schema(description = "Latest publish timestamp in UTC ISO-8601 format")
    private Instant publishedTime;

    @Schema(description = "Creation timestamp in UTC ISO-8601 format")
    private Instant createTime;

    @Schema(description = "Last update timestamp in UTC ISO-8601 format")
    private Instant updateTime;
}
