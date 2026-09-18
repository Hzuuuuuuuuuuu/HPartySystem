package com.hparty.party.enums;

import lombok.Getter;

/**
 * 党纪学习教育类型。
 * <p>与 {@code discipline_study.study_type} 取值一一对应。</p>
 */
@Getter
public enum DisciplineStudyTypeEnum {

    REGULATION(1, "条例学习"),
    WARNING_EDUCATION(2, "警示教育"),
    SPECIAL_LECTURE(3, "专题党课"),
    KNOWLEDGE_TEST(4, "知识测试"),
    CASE_STUDY(5, "案例研讨");

    private final Integer code;
    private final String label;

    DisciplineStudyTypeEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public static DisciplineStudyTypeEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (DisciplineStudyTypeEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    /** 取中文标签，未知取值返回空串 */
    public static String labelOf(Integer code) {
        DisciplineStudyTypeEnum e = of(code);
        return e == null ? "" : e.label;
    }
}
