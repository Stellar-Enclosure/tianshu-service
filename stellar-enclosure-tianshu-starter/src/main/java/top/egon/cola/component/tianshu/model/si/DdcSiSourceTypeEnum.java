package top.egon.cola.component.tianshu.model.si;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Registration origin of an SI resource.
 */
public enum DdcSiSourceTypeEnum {
    CONSOLE("CONSOLE"),
    AUTO_REPORTED("AUTO_REPORTED"),
    GITOPS("GITOPS");

    private final String code;

    DdcSiSourceTypeEnum(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static DdcSiSourceTypeEnum fromCode(String code) {
        for (DdcSiSourceTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown SI source type: " + code);
    }
}
