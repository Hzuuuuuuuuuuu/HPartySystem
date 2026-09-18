package com.hparty.common.enums;

import lombok.Getter;

/**
 * 人员状态：打通「群众 → 入党申请人 → 积极分子 → 发展对象 → 预备党员 → 正式党员」全生命周期。
 * <p>与 {@code party_person.member_status} 对应，也与 {@code dev_stage} 五个阶段形成映射。</p>
 */
@Getter
public enum MemberStatus {

    MASS(0, "群众", null),
    APPLICANT(1, "入党申请人", "STAGE_1"),
    ACTIVIST(2, "入党积极分子", "STAGE_2"),
    CANDIDATE(3, "发展对象", "STAGE_3"),
    PROBATIONARY(4, "预备党员", "STAGE_4"),
    FULL_MEMBER(5, "正式党员", null),
    FLOWING(6, "流动党员", null);

    private final int code;
    private final String label;
    /** 对应的发展阶段编码，非发展对象阶段为 null */
    private final String stageCode;

    MemberStatus(int code, String label, String stageCode) {
        this.code = code;
        this.label = label;
        this.stageCode = stageCode;
    }

    public static MemberStatus of(Integer code) {
        if (code == null) {
            return MASS;
        }
        for (MemberStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return MASS;
    }

    public static String labelOf(Integer code) {
        return of(code).getLabel();
    }

    /** 是否已具备党员身份（预备党员或正式党员） */
    public static boolean isPartyMember(Integer code) {
        return code != null && (code == PROBATIONARY.code || code == FULL_MEMBER.code || code == FLOWING.code);
    }

    /** 是否处于发展流程中 */
    public static boolean isInDevelopFlow(Integer code) {
        return code != null && code >= APPLICANT.code && code <= PROBATIONARY.code;
    }
}
