package com.hparty.party.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 民主评议党员明细（一人一条）。
 *
 * <p>该表只有 {@code create_time} 一个审计列（评议明细一经生成不再变更归属），
 * 因此**不继承 {@code BaseEntity}**，自行声明 create_time —— 见
 * {@code docs/03-数据库设计.md} 2.1 与 {@code docs/05-开发规范.md} 4.1。</p>
 */
@Data
@TableName("party_review_detail")
public class PartyReviewDetail implements Serializable {

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

    /** 自评意见 */
    private String selfComment;

    /** 自评提交时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime selfTime;

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

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
