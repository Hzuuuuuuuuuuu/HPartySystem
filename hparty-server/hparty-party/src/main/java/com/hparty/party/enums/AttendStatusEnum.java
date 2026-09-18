package com.hparty.party.enums;

import lombok.Getter;

/**
 * 参与情况（教育活动参与记录、党纪学习参与记录共用）。
 * <p>与 {@code edu_participant.attend_status}、{@code discipline_participant.attend_status} 取值一一对应。</p>
 */
@Getter
public enum AttendStatusEnum {

    NOT_SIGNED(0, "未签到"),
    ATTENDED(1, "已参加"),
    LEAVE(2, "请假"),
    ABSENT(3, "缺席");

    private final Integer code;
    private final String label;

    AttendStatusEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public static AttendStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (AttendStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    /** 取中文标签，未知取值返回空串 */
    public static String labelOf(Integer code) {
        AttendStatusEnum e = of(code);
        return e == null ? "" : e.label;
    }
}
