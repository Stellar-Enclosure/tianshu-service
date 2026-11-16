package top.egon.cola.component.tianshu.model.si;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Target classification for directed SI dependency and Tianji workflow edges.
 */
public enum DdcSiReferenceTargetTypeEnum {
    AGENT("AGENT"),
    MCP("MCP"),
    PROMPT("PROMPT"),
    SKILL("SKILL"),
    TIANJI_PROCESS("TIANJI_PROCESS");

    private final String code;

    DdcSiReferenceTargetTypeEnum(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static DdcSiReferenceTargetTypeEnum fromCode(String code) {
        for (DdcSiReferenceTargetTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown SI reference target type: " + code);
    }
}
