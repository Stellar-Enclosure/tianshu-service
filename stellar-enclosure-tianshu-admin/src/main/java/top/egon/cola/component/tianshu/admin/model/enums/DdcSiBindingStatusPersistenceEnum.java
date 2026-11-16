package top.egon.cola.component.tianshu.admin.model.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import top.egon.cola.component.common.core.enums.EgonEnum;

/** SI 应用服务绑定状态持久化枚举。 */
public enum DdcSiBindingStatusPersistenceEnum implements EgonEnum {
    ACTIVE("ACTIVE", 401, "Active"),
    INACTIVE("INACTIVE", 402, "Inactive");

    @EnumValue
    private final String value;
    private final int code;
    private final String message;

    DdcSiBindingStatusPersistenceEnum(String value, int code, String message) {
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

    public static DdcSiBindingStatusPersistenceEnum fromValue(String value) {
        for (DdcSiBindingStatusPersistenceEnum item : values()) {
            if (item.value.equals(value)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Unknown SI binding status persistence value");
    }
}
