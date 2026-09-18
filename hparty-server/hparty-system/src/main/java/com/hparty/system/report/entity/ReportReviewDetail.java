package com.hparty.system.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 导出专用的 {@code party_review_detail} 最小投影（见 {@link ReportDuesRecord} 的说明）。
 *
 * <p>该表没有 {@code del_flag}（评议明细不做逻辑删除），因此没有 {@code @TableLogic}。</p>
 */
@Data
@TableName("party_review_detail")
public class ReportReviewDetail implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 明细ID */
    @TableId(value = "detail_id", type = IdType.AUTO)
    private Long detailId;

    /** 评议批次ID */
    private Long reviewId;

    /** 人员ID */
    private Long personId;

    /** 姓名（冗余） */
    private String personName;

    /** 组织ID */
    private Long orgId;

    /** 自评得分 */
    private BigDecimal selfScore;

    /** 互评平均分 */
    private BigDecimal peerScore;

    /** 参与互评人数 */
    private Integer peerCount;

    /** 群众评议得分 */
    private BigDecimal massScore;

    /** 组织评定得分 */
    private BigDecimal orgScore;

    /** 综合得分 */
    private BigDecimal totalScore;

    /** 等次：1=优秀 2=合格 3=基本合格 4=不合格 */
    private Integer grade;

    /** 组织评定意见 */
    private String orgComment;

    /** 对不合格党员的处置意见 */
    private String dispose;
}
