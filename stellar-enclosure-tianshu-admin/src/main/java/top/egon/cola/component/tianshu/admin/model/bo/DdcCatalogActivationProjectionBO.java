package top.egon.cola.component.tianshu.admin.model.bo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import top.egon.cola.component.common.mybatis.model.EgonColaModelValidationGroups;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.Accessors;
import lombok.Builder;
import java.time.Instant;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DdcCatalogActivationProjectionBO {
    @JsonIgnore private Long id;
    @JsonIgnore private Long tenantId;
    @JsonIgnore private String createUserId;
    @JsonIgnore private Instant createTime;
    @JsonIgnore private String updateUserId;
    @JsonIgnore private Instant updateTime;
    @JsonIgnore private LocalDateTime deletedAt;
    @JsonIgnore private Long version;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Positive(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private Long catalogScopeId;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Positive(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private Long activationVersion;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 64, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String descriptorDigest;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String descriptorJson;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Positive(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private Integer producerCount;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 2048, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String authorityResourceUri;
}
