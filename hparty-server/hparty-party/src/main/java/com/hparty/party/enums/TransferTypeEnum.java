package com.hparty.party.enums;

import lombok.Getter;

/**
 * 组织关系转接类型。
 * <p>与 {@code party_transfer.transfer_type} 取值一一对应。</p>
 */
@Getter
public enum TransferTypeEnum {

    OUT(1, "转出"),
    IN(2, "转入"),
    INNER(3, "内部调整");

    private final Integer code;
    private final String label;

    TransferTypeEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public static TransferTypeEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (TransferTypeEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    /** 取中文标签，未知取值返回空串 */
    public static String labelOf(Integer code) {
        TransferTypeEnum e = of(code);
        return e == null ? "" : e.label;
    }
}
