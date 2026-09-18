package com.hparty.party.domain.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 民主评议·自评提交参数。
 *
 * <p>自评只能由**本人**提交，因此请求体里不带 {@code personId} ——
 * 打分对象由服务端从登录会话取，避免越权替他人自评。</p>
 */
@Data
public class ReviewSelfEvalDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 自评得分（0-100） */
    @NotNull(message = "请填写自评得分")
    @DecimalMin(value = "0", message = "自评得分不能小于 0")
    @DecimalMax(value = "100", message = "自评得分不能大于 100")
    private BigDecimal selfScore;

    /** 自评意见 */
    private String selfComment;
}
