package top.egon.cola.component.tianshu.model.si;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Super-Intelligence (SI) logical resource classification.
 */
public enum DdcSiKindEnum {
    AGENT("AGENT"),
    MCP("MCP"),
    PROMPT("PROMPT"),
    SKILL("SKILL");

    private final String code;

    DdcSiKindEnum(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static DdcSiKindEnum fromCode(String code) {
        for (DdcSiKindEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown SI resource kind: " + code);
    }
}
