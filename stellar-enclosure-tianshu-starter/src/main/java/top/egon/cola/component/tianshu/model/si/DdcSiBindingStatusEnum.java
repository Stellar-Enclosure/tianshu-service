package top.egon.cola.component.tianshu.model.si;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Activation state for application-scoped SI version bindings.
 */
public enum DdcSiBindingStatusEnum {
    ACTIVE("ACTIVE"),
    INACTIVE("INACTIVE");

    private final String code;

    DdcSiBindingStatusEnum(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static DdcSiBindingStatusEnum fromCode(String code) {
        for (DdcSiBindingStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown SI binding status: " + code);
    }
}
