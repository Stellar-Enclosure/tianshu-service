package top.egon.cola.component.tianshu.admin.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DdcI18nScope(
        @NotBlank @Size(max = 128) @Pattern(regexp = "^[^\\p{Cntrl}]+$") String bizCode,
        @NotBlank @Size(max = 128) @Pattern(regexp = "^[^\\p{Cntrl}]+$") String namespaceCode,
        @NotBlank @Size(max = 32) @Pattern(regexp = "^[^\\p{Cntrl}]+$") String env,
        @NotBlank @Size(max = 128) @Pattern(regexp = "^[^\\p{Cntrl}]+$") String appCode) {
}
