package com.hparty.party.enums;

import lombok.Getter;

/**
 * 组织关系转接状态。
 * <p>与 {@code party_transfer.status} 取值一一对应。</p>
 */
@Getter
public enum TransferStatusEnum {

    PENDING(0, "待提交"),
    ISSUED(1, "已开具"),
    ACCEPTED(2, "已接收"),
    REJECTED(3, "已拒绝"),
    EXPIRED(4, "已超期"),
    REVOKED(5, "已撤销");

    private final Integer code;
    private final String label;

    TransferStatusEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public static TransferStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (TransferStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    /** 取中文标签，未知取值返回空串 */
    public static String labelOf(Integer code) {
        TransferStatusEnum e = of(code);
        return e == null ? "" : e.label;
    }
}
