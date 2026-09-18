package com.hparty.party.domain.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 民主评议·互评提交参数（批量）。
 *
 * <p>一次请求提交「我」对若干名同志的打分。**打分人由登录会话决定，请求体里没有打分人字段**，
 * 因此无法伪造他人身份打分；同时服务端会拒绝任何把自己作为打分对象的条目
 * （见 {@code PartyReviewService.submitPeerEval}）。</p>
 */
@Data
public class ReviewPeerEvalDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 打分条目列表 */
    @NotNull(message = "请填写互评打分")
    private List<Item> items;

    /**
     * 单条打分。
     */
    @Data
    public static class Item implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 被评议人 person_id */
        @NotNull(message = "被评议人不能为空")
        private Long personId;

        /** 得分（0-100） */
        @NotNull(message = "请填写得分")
        @DecimalMin(value = "0", message = "得分不能小于 0")
        @DecimalMax(value = "100", message = "得分不能大于 100")
        private BigDecimal score;
    }
}
