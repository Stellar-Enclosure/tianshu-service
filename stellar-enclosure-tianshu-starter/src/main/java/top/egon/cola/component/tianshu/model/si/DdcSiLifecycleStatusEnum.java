package top.egon.cola.component.tianshu.model.si;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Lifecycle state for SI resources and immutable snapshot versions.
 */
public enum DdcSiLifecycleStatusEnum {
    DRAFT("DRAFT"),
    PUBLISHED("PUBLISHED"),
    DEPRECATED("DEPRECATED"),
    ARCHIVED("ARCHIVED");

    private final String code;

    DdcSiLifecycleStatusEnum(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    /**
     * Only published or deprecated versions remain resolvable for active/graceful runtime execution.
     */
    public boolean isQueryableByRuntime() {
        return this == PUBLISHED || this == DEPRECATED;
    }

    @JsonCreator
    public static DdcSiLifecycleStatusEnum fromCode(String code) {
        for (DdcSiLifecycleStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown SI lifecycle status: " + code);
    }
}
