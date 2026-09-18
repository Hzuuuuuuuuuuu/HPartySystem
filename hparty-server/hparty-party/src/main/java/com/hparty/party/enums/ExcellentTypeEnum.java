package com.hparty.party.enums;

import lombok.Getter;

/**
 * 先优评选类型。
 * <p>与 {@code excellent_selection.selection_type} 取值一一对应。</p>
 */
@Getter
public enum ExcellentTypeEnum {

    EXCELLENT_MEMBER(1, "优秀共产党员"),
    EXCELLENT_WORKER(2, "优秀党务工作者"),
    ADVANCED_ORG(3, "先进基层党组织");

    private final Integer code;
    private final String label;

    ExcellentTypeEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public static ExcellentTypeEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ExcellentTypeEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    /** 取中文标签，未知取值返回空串 */
    public static String labelOf(Integer code) {
        ExcellentTypeEnum e = of(code);
        return e == null ? "" : e.label;
    }
}
