package com.hparty.party.enums;

import lombok.Getter;

/**
 * 党纪学习教育状态。
 * <p>与 {@code discipline_study.status} 取值一一对应。</p>
 */
@Getter
public enum DisciplineStatusEnum {

    DRAFT(0, "草稿"),
    RUNNING(1, "进行中"),
    FINISHED(2, "已结束");

    private final Integer code;
    private final String label;

    DisciplineStatusEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public static DisciplineStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (DisciplineStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    /** 取中文标签，未知取值返回空串 */
    public static String labelOf(Integer code) {
        DisciplineStatusEnum e = of(code);
        return e == null ? "" : e.label;
    }
}
