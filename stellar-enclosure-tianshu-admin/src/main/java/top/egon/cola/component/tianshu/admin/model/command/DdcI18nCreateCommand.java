package top.egon.cola.component.tianshu.admin.model.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record DdcI18nCreateCommand(
        @NotBlank @Size(max = 128) String bizCode,
        @NotBlank @Size(max = 128) String namespaceCode,
        @NotBlank @Size(max = 32) String env,
        @NotBlank @Size(max = 128) String appCode,
        @NotBlank @Size(max = 128) String pageCode,
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 64) String defaultLocale,
        @NotNull Map<String, Map<String, String>> translations) {
}
