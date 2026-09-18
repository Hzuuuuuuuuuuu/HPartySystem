package com.hparty.party.enums;

import lombok.Getter;

/**
 * 党费使用用途分类。
 * <p>与 {@code party_dues_use.use_category} 取值一一对应。</p>
 */
@Getter
public enum DuesUseCategoryEnum {

    EDUCATION(1, "党员教育"),
    COMMENDATION(2, "表彰奖励"),
    ASSISTANCE(3, "困难帮扶"),
    POSITION_BUILD(4, "阵地建设"),
    NEWSPAPER(5, "订阅报刊"),
    OTHER(6, "其它");

    private final Integer code;
    private final String label;

    DuesUseCategoryEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public static DuesUseCategoryEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (DuesUseCategoryEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    /** 取中文标签，未知取值返回空串 */
    public static String labelOf(Integer code) {
        DuesUseCategoryEnum e = of(code);
        return e == null ? "" : e.label;
    }
}
