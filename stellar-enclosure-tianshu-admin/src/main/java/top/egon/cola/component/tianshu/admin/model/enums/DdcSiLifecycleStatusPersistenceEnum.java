package top.egon.cola.component.tianshu.admin.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import top.egon.cola.component.common.core.enums.EgonEnum;

/** SI 资源生命周期状态持久化枚举。 */
public enum DdcSiLifecycleStatusPersistenceEnum implements EgonEnum {
    DRAFT("DRAFT", 301, "Draft"),
    PUBLISHED("PUBLISHED", 302, "Published"),
    DEPRECATED("DEPRECATED", 303, "Deprecated"),
    ARCHIVED("ARCHIVED", 304, "Archived");

    @EnumValue
    private final String value;
    private final int code;
    private final String message;

    DdcSiLifecycleStatusPersistenceEnum(String value, int code, String message) {
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

    public static DdcSiLifecycleStatusPersistenceEnum fromValue(String value) {
        for (DdcSiLifecycleStatusPersistenceEnum item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Unknown SI lifecycle status persistence value");
    }
}
