package com.hparty.party.enums;

import lombok.Getter;

/**
 * 党员教育活动状态。
 * <p>与 {@code edu_activity.status} 取值一一对应。</p>
 */
@Getter
public enum EduActivityStatusEnum {

    DRAFT(0, "草稿"),
    ENROLLING(1, "报名中"),
    RUNNING(2, "进行中"),
    FINISHED(3, "已结束");

    private final Integer code;
    private final String label;

    EduActivityStatusEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public static EduActivityStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (EduActivityStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    /** 取中文标签，未知取值返回空串 */
    public static String labelOf(Integer code) {
        EduActivityStatusEnum e = of(code);
        return e == null ? "" : e.label;
    }
}
