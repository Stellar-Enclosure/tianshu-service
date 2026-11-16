package top.egon.cola.component.tianshu.admin.model.bo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.component.common.mybatis.model.EgonColaModelValidationGroups;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiSourceTypeEnum;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/** SI 统一治理主资源领域聚合业务对象。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DdcSiResourceBO {

    private Long id;
    @JsonIgnore
    private Long tenantId;
    @JsonIgnore
    private String createUserId;
    private Instant createTime;
    @JsonIgnore
    private String updateUserId;
    private Instant updateTime;
    @JsonIgnore
    private LocalDateTime deletedAt;
    private Long version;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Update.class,
            EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 128)
    private String bizCode;

    @Positive(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private Long namespaceId;

    private String namespaceCode;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 32)
    private String envCode;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 128)
    private String appCode;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private DdcSiKindEnum kind;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
    private String resourceKey;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Update.class,
            EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 128)
    private String displayName;

    @Size(max = 2048)
    @Builder.Default
    private String description = "";

    @Builder.Default
    private List<String> tags = Collections.emptyList();

    @NotNull
    private DdcSiLifecycleStatusEnum lifecycleStatus;

    @NotNull
    private DdcSiSourceTypeEnum sourceType;

    @NotNull
    private String specJson;

    @Pattern(regexp = "^[0-9a-f]{64}$")
    private String contentSha256;

    @Min(0)
    @Builder.Default
    private Long lastVersionNo = 0L;

    private Long currentVersionId;

    private Long currentVersionNo;

    private Instant publishedTime;
}
