package top.egon.cola.component.tianshu.model.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Stable wire result of processing one configuration target; it grants no execution authority. */
public enum DdcConfigReceiptStatusEnum {
    APPLIED("APPLIED"),
    DISABLED_APPLIED("DISABLED_APPLIED"),
    REJECTED("REJECTED"),
    RESTART_REQUIRED("RESTART_REQUIRED"),
    FAILED("FAILED");

    private final String code;

    DdcConfigReceiptStatusEnum(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public boolean isSuccessful() {
        return this == APPLIED || this == DISABLED_APPLIED;
    }

    @JsonCreator
    public static DdcConfigReceiptStatusEnum fromCode(String code) {
        for (DdcConfigReceiptStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("CONFIG_RECEIPT_STATUS_INVALID");
    }
}
