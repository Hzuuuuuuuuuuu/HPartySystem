package com.hparty.party.enums;

import lombok.Getter;

/**
 * 党员教育活动类型。
 * <p>与 {@code edu_activity.activity_type} 取值一一对应。</p>
 */
@Getter
public enum EduActivityTypeEnum {

    PARTY_LECTURE(1, "党课"),
    SPECIAL_TRAINING(2, "专题培训"),
    ONLINE_STUDY(3, "在线学习"),
    PRACTICE(4, "实践锻炼"),
    ROTATION_TRAINING(5, "集中轮训");

    private final Integer code;
    private final String label;

    EduActivityTypeEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public static EduActivityTypeEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (EduActivityTypeEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    /** 取中文标签，未知取值返回空串 */
    public static String labelOf(Integer code) {
        EduActivityTypeEnum e = of(code);
        return e == null ? "" : e.label;
    }
}
