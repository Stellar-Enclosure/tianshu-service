package top.egon.cola.component.tianshu.admin.model.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import top.egon.cola.component.common.mybatis.model.EgonColaModelValidationGroups;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import top.egon.cola.component.common.mybatis.model.EgonModel;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ddc_i18n_page", autoResultMap = true)
public class DdcI18nPagePO extends EgonModel<DdcI18nPagePO> {
    @TableField(value = "biz_code", updateStrategy = FieldStrategy.NEVER)
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 128, groups = {EgonColaModelValidationGroups.Insert.class,
            EgonColaModelValidationGroups.Update.class, EgonColaModelValidationGroups.Persisted.class})
    private String bizCode;

    @TableField(value = "namespace_id", updateStrategy = FieldStrategy.NEVER)
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Positive(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private Long namespaceId;

    @TableField(value = "env_code", updateStrategy = FieldStrategy.NEVER)
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 32, groups = {EgonColaModelValidationGroups.Insert.class,
            EgonColaModelValidationGroups.Update.class, EgonColaModelValidationGroups.Persisted.class})
    private String envCode;

    @TableField(value = "app_code", updateStrategy = FieldStrategy.NEVER)
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 128, groups = {EgonColaModelValidationGroups.Insert.class,
            EgonColaModelValidationGroups.Update.class, EgonColaModelValidationGroups.Persisted.class})
    private String appCode;

    @TableField(value = "page_code", updateStrategy = FieldStrategy.NEVER)
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 128, groups = {EgonColaModelValidationGroups.Insert.class,
            EgonColaModelValidationGroups.Update.class, EgonColaModelValidationGroups.Persisted.class})
    private String pageCode;

    @TableField("name")
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 128, groups = {EgonColaModelValidationGroups.Insert.class,
            EgonColaModelValidationGroups.Update.class, EgonColaModelValidationGroups.Persisted.class})
    private String name;

    @TableField("default_locale")
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 64, groups = {EgonColaModelValidationGroups.Insert.class,
            EgonColaModelValidationGroups.Update.class, EgonColaModelValidationGroups.Persisted.class})
    private String defaultLocale;

    @TableField("draft_translations")
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 2097152, groups = {EgonColaModelValidationGroups.Insert.class,
            EgonColaModelValidationGroups.Update.class, EgonColaModelValidationGroups.Persisted.class})
    private String draftTranslations;

    @TableField("published_default_locale")
    private String publishedDefaultLocale;

    @TableField("published_translations")
    private String publishedTranslations;

    @TableField("published_version")
    private Long publishedVersion;

    @TableField(value = "catalog_scope_id", updateStrategy = FieldStrategy.NEVER)
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Positive(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private Long catalogScopeId;

    @TableField("draft_activation_version")
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Positive(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private Long draftActivationVersion;

    @TableField("draft_source_hashes")
    @NotNull(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    @Size(max = 2097152, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String draftSourceHashes;

    @TableField("current_override_id")
    @Positive(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private Long currentOverrideId;

    @TableField("published_source_hashes")
    @Size(max = 2097152, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String publishedSourceHashes;

    @TableField("published_draft_revision")
    @PositiveOrZero(groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private Long publishedDraftRevision;

    @TableField("published_operation_id")
    @Size(max = 128, groups = {EgonColaModelValidationGroups.Insert.class, EgonColaModelValidationGroups.Persisted.class})
    private String publishedOperationId;

    @TableField("published_at")
    private Instant publishedAt;

    @TableField(exist = false)
    private String namespaceCode;
}
