package top.egon.cola.component.tianshu.admin.model.bo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import top.egon.cola.component.common.mybatis.model.EgonColaModelValidationGroups;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.experimental.Accessors;
import java.time.Instant;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DdcI18nPageBO {
    @JsonIgnore private Long id;
    @JsonIgnore private Long tenantId;
    @JsonIgnore private String createUserId;
    @JsonIgnore private Instant createTime;
    @JsonIgnore private String updateUserId;
    @JsonIgnore private Instant updateTime;
    @JsonIgnore private LocalDateTime deletedAt;
    @JsonIgnore private Long version;
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 128, groups = {EgonColaModelValidationGroups.Insert.class,
            EgonColaModelValidationGroups.Update.class, EgonColaModelValidationGroups.Persisted.class})
    private String bizCode;
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Positive(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private Long namespaceId;
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 32, groups = {EgonColaModelValidationGroups.Insert.class,
            EgonColaModelValidationGroups.Update.class, EgonColaModelValidationGroups.Persisted.class})
    private String envCode;
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 128, groups = {EgonColaModelValidationGroups.Insert.class,
            EgonColaModelValidationGroups.Update.class, EgonColaModelValidationGroups.Persisted.class})
    private String appCode;
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 128, groups = {EgonColaModelValidationGroups.Insert.class,
            EgonColaModelValidationGroups.Update.class, EgonColaModelValidationGroups.Persisted.class})
    private String pageCode;
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 128, groups = {EgonColaModelValidationGroups.Insert.class,
            EgonColaModelValidationGroups.Update.class, EgonColaModelValidationGroups.Persisted.class})
    private String name;
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 64, groups = {EgonColaModelValidationGroups.Insert.class,
            EgonColaModelValidationGroups.Update.class, EgonColaModelValidationGroups.Persisted.class})
    private String defaultLocale;
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 2097152, groups = {EgonColaModelValidationGroups.Insert.class,
            EgonColaModelValidationGroups.Update.class, EgonColaModelValidationGroups.Persisted.class})
    private String draftTranslations;
    private String publishedDefaultLocale;
    private String publishedTranslations;
    private Long publishedVersion;
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Positive(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private Long catalogScopeId;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Positive(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private Long draftActivationVersion;

    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 2097152, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String draftSourceHashes;

    @Positive(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private Long currentOverrideId;

    @Size(max = 2097152, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String publishedSourceHashes;

    @PositiveOrZero(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private Long publishedDraftRevision;

    @Size(max = 128, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String publishedOperationId;

    private Instant publishedAt;

    private String namespaceCode;
}
