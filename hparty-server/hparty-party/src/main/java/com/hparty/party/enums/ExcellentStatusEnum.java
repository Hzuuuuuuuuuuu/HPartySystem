package com.hparty.party.enums;

import lombok.Getter;

/**
 * 先优评选活动状态。
 * <p>与 {@code excellent_selection.status} 取值一一对应。</p>
 */
@Getter
public enum ExcellentStatusEnum {

    DRAFT(0, "草稿"),
    RECOMMENDING(1, "推荐中"),
    REVIEWING(2, "评审中"),
    PUBLICIZED(3, "已公示"),
    COMMENDED(4, "已表彰");

    private final Integer code;
    private final String label;

    ExcellentStatusEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public static ExcellentStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ExcellentStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    /** 取中文标签，未知取值返回空串 */
    public static String labelOf(Integer code) {
        ExcellentStatusEnum e = of(code);
        return e == null ? "" : e.label;
    }
}
