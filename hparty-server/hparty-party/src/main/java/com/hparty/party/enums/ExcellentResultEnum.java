package com.hparty.party.enums;

import lombok.Getter;

/**
 * 先优评选候选人结果。
 * <p>与 {@code excellent_candidate.result} 取值一一对应。</p>
 */
@Getter
public enum ExcellentResultEnum {

    PENDING(0, "待评审"),
    RECOMMENDED(1, "已推荐"),
    AWARDED(2, "已获奖"),
    NOT_AWARDED(3, "未获奖");

    private final Integer code;
    private final String label;

    ExcellentResultEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public static ExcellentResultEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ExcellentResultEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    /** 取中文标签，未知取值返回空串 */
    public static String labelOf(Integer code) {
        ExcellentResultEnum e = of(code);
        return e == null ? "" : e.label;
    }
}
