package com.hparty.party.enums;

import lombok.Getter;

/**
 * 党费缴纳方式。
 * <p>与 {@code party_dues_record.pay_type} 取值一一对应。</p>
 */
@Getter
public enum DuesPayTypeEnum {

    CASH(1, "现金"),
    BANK(2, "银行代扣"),
    WECHAT(3, "微信"),
    ALIPAY(4, "支付宝"),
    OTHER(5, "其它");

    private final Integer code;
    private final String label;

    DuesPayTypeEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public static DuesPayTypeEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (DuesPayTypeEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    /** 取中文标签，未知取值返回空串 */
    public static String labelOf(Integer code) {
        DuesPayTypeEnum e = of(code);
        return e == null ? "" : e.label;
    }
}
