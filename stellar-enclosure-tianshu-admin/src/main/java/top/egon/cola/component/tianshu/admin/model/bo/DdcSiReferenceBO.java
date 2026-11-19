package top.egon.cola.component.tianshu.admin.model.bo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiReferenceTargetTypeEnum;

import java.time.Instant;
import java.time.LocalDateTime;

/** SI 资源与流程有向依赖引用业务对象。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DdcSiReferenceBO {

    private Long id;
    private Long tenantId;
    private String createUserId;
    private Instant createTime;
    private String updateUserId;
    private Instant updateTime;
    private LocalDateTime deletedAt;
    private Long version;

    private Long sourceResourceId;
    private Long sourceVersionNo;
    private DdcSiReferenceTargetTypeEnum sourceType;
    private String sourceKey;
    private DdcSiReferenceTargetTypeEnum targetType;
    private DdcSiKindEnum targetKind;
    private String targetKey;
    private Long targetVersionNo;
    private Long pinnedVersionNo;
    private String envCode;
    private Long namespaceId;
    @Builder.Default
    private boolean required = true;
    private String roleHint;

    /** Internal publication constraint, deliberately absent from the editable/persisted reference projection. */
    @Valid
    @JsonIgnore
    @Nullable
    private ExpectedIdentityVO expectedIdentity;

    /** Exact immutable tuple; owner presence distinguishes inheritance from explicit business ownership. */
    public record ExpectedIdentityVO(
            @NotNull @Positive Long resourceId,
            @NotNull @Positive Long versionId,
            @NotNull @Positive Long versionNo,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String digestSha256,
            boolean ownerPresent,
            @Nullable @Size(max = 128) String ownerAppCode) {
        public ExpectedIdentityVO {
            if (resourceId == null || resourceId <= 0 || versionId == null || versionId <= 0
                    || versionNo == null || versionNo <= 0 || digestSha256 == null
                    || !digestSha256.matches("[0-9a-f]{64}")
                    || (!ownerPresent && ownerAppCode != null)
                    || (ownerAppCode != null && (ownerAppCode.isBlank() || ownerAppCode.length() > 128
                            || !ownerAppCode.equals(ownerAppCode.strip())
                            || ownerAppCode.codePoints().anyMatch(Character::isISOControl)))) {
                throw new IllegalArgumentException("SI_DEPENDENCY_EXPECTED_IDENTITY_INVALID");
            }
        }
    }
}
