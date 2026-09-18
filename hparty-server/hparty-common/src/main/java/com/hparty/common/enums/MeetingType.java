package com.hparty.common.enums;

import lombok.Getter;

/**
 * 会议类型：三会一课 + 主题党日 + 组织生活会。
 */
@Getter
public enum MeetingType {

    MEMBER_ASSEMBLY("MEMBER_ASSEMBLY", "党员大会", "三会一课"),
    BRANCH_COMMITTEE("BRANCH_COMMITTEE", "支部委员会", "三会一课"),
    PARTY_GROUP("PARTY_GROUP", "党小组会", "三会一课"),
    PARTY_LECTURE("PARTY_LECTURE", "党课", "三会一课"),
    THEME_PARTY_DAY("THEME_PARTY_DAY", "主题党日", "主题党日"),
    ORG_LIFE("ORG_LIFE", "组织生活会", "组织生活会");

    private final String code;
    private final String label;
    private final String category;

    MeetingType(String code, String label, String category) {
        this.code = code;
        this.label = label;
        this.category = category;
    }

    public static MeetingType of(String code) {
        for (MeetingType t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        return null;
    }

    public static String labelOf(String code) {
        MeetingType t = of(code);
        return t == null ? "" : t.label;
    }
}
