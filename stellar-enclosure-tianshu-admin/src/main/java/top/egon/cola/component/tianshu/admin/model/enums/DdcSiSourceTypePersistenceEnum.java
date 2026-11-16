package top.egon.cola.component.tianshu.admin.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import top.egon.cola.component.common.core.enums.EgonEnum;

/** SI 资源来源类型持久化枚举。 */
public enum DdcSiSourceTypePersistenceEnum implements EgonEnum {
    CONSOLE("CONSOLE", 501, "Console"),
    AUTO_REPORTED("AUTO_REPORTED", 502, "AutoReported"),
    GITOPS("GITOPS", 503, "GitOps");

    @EnumValue
    private final String value;
    private final int code;
    private final String message;

    DdcSiSourceTypePersistenceEnum(String value, int code, String message) {
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

    public static DdcSiSourceTypePersistenceEnum fromValue(String value) {
        for (DdcSiSourceTypePersistenceEnum item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Unknown SI source type persistence value");
    }
}
