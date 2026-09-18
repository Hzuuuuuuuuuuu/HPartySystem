package com.hparty.common.exception;

import com.hparty.common.core.ResultCode;
import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常。由全局异常处理器转换为统一响应体。
 */
@Getter
public class BizException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;

    public BizException(String msg) {
        super(msg);
        this.code = ResultCode.BIZ_ERROR.getCode();
    }

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public BizException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
    }

    public BizException(ResultCode resultCode, String msg) {
        super(msg);
        this.code = resultCode.getCode();
    }

    /** 条件成立时抛出，用于简化校验代码 */
    public static void throwIf(boolean condition, String msg) {
        if (condition) {
            throw new BizException(msg);
        }
    }

    /** 数据/功能权限不足：统一使用 403 业务码。 */
    public static BizException forbidden(String msg) {
        return new BizException(ResultCode.FORBIDDEN, msg);
    }

    /** 条件成立时按 403 抛出，用于数据权限边界校验。 */
    public static void throwForbiddenIf(boolean condition, String msg) {
        if (condition) {
            throw forbidden(msg);
        }
    }

    /** 流程规则校验未通过 */
    public static BizException ruleReject(String msg) {
        return new BizException(ResultCode.RULE_REJECT, msg);
    }
}
