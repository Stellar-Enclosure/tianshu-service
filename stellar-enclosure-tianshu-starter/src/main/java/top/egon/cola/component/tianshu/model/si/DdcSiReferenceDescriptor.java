package top.egon.cola.component.tianshu.model.si;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.lang.Nullable;

/**
 * Directed dependency reference declared by an SI resource or Tianji workflow.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DdcSiReferenceDescriptor {

    @NotNull
    private DdcSiReferenceTargetTypeEnum targetType;

    @NotBlank
    private String targetKey;

    @Nullable
    @Positive
    private Integer pinnedVersionNo;

    @Builder.Default
    private boolean required = true;

    @Nullable
    private String roleHint;
}
