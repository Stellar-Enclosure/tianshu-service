package top.egon.cola.component.tianshu.admin.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import top.egon.cola.component.common.core.enums.EgonEnum;

/** SI 有向引用边目标类型持久化枚举。 */
public enum DdcSiReferenceTargetTypePersistenceEnum implements EgonEnum {
    AGENT("AGENT", 601, "Agent"),
    MCP("MCP", 602, "Mcp"),
    PROMPT("PROMPT", 603, "Prompt"),
    SKILL("SKILL", 604, "Skill"),
    TIANJI_PROCESS("TIANJI_PROCESS", 605, "TianjiProcess");

    @EnumValue
    private final String value;
    private final int code;
    private final String message;

    DdcSiReferenceTargetTypePersistenceEnum(String value, int code, String message) {
        this.value = value;
        this.code = code;
        this.message = message;
    }

    public String getValue() {
        return value;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public static DdcSiReferenceTargetTypePersistenceEnum fromValue(String value) {
        for (DdcSiReferenceTargetTypePersistenceEnum item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Unknown SI reference target type persistence value");
    }
}
