package top.egon.cola.component.tianshu.admin.model.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record DdcI18nDraftCommand(
        @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]{0,18})$") String expectedRevision,
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 64) String defaultLocale,
        @NotNull Map<String, Map<String, String>> translations) {
}
