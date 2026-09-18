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
 * 民主评议·组织评定参数（批量）。
 *
 * <p>{@code grade} 可不填，此时由综合得分按阈值自动判定（见 {@code ReviewGradeEnum.of}）；
 * 填了则以组织意见为准。{@code massScore} 是「群众评议」分项，与组织评定一并录入 ——
 * 群众评议没有独立的党员账号入口，实际工作中由支部汇总后录入。</p>
 */
@Data
public class ReviewOrgEvalDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 评定条目列表 */
    @NotNull(message = "请填写组织评定")
    private List<Item> items;

    /**
     * 单条评定。
     */
    @Data
    public static class Item implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 明细ID */
        @NotNull(message = "评议明细ID不能为空")
        private Long detailId;

        /** 组织评定得分（0-100） */
        @DecimalMin(value = "0", message = "组织评定得分不能小于 0")
        @DecimalMax(value = "100", message = "组织评定得分不能大于 100")
        private BigDecimal orgScore;

        /** 群众评议得分（0-100），可空 */
        @DecimalMin(value = "0", message = "群众评议得分不能小于 0")
        @DecimalMax(value = "100", message = "群众评议得分不能大于 100")
        private BigDecimal massScore;

        /** 等次：1=优秀 2=合格 3=基本合格 4=不合格；不填则按综合得分自动判定 */
        private Integer grade;

        /** 组织评定意见 */
        private String orgComment;

        /** 对不合格党员的处置意见（限期改正/劝退/除名） */
        private String dispose;
    }
}
