package com.hparty.party.enums;

import lombok.Getter;

/**
 * 党费缴纳状态。
 * <p>与 {@code party_dues_record.status} 取值一一对应。</p>
 */
@Getter
public enum DuesStatusEnum {

    UNPAID(0, "未缴"),
    PAID(1, "已缴"),
    EXEMPT(2, "免缴"),
    SUPPLEMENT(3, "补缴");

    private final Integer code;
    private final String label;

    DuesStatusEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public static DuesStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (DuesStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    /** 取中文标签，未知取值返回空串 */
    public static String labelOf(Integer code) {
        DuesStatusEnum e = of(code);
        return e == null ? "" : e.label;
    }
}
