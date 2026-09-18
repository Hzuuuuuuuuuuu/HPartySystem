package com.hparty.party.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.hparty.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 民主评议党员批次
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("party_review")
public class PartyReview extends BaseEntity {

    /** 评议批次ID */
    @TableId(value = "review_id", type = IdType.AUTO)
    private Long reviewId;

    /** 标题，如：2026年度民主评议党员 */
    private String title;

    /** 组织ID */
    private Long orgId;

    /** 评议年度 */
    private Integer reviewYear;

    /** 自评开始日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /** 评议截止日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    /** 状态：0=草稿 1=自评中 2=互评中 3=组织评定中 4=已公示 5=已完成 */
    private Integer status;

    /** 优秀名额（一般不超过党员总数30%） */
    private Integer excellentQuota;

    /** 评议说明 */
    private String description;

    /** 评议结果材料文件ID */
    private Long fileId;

    /** 评议结果材料URL */
    private String fileUrl;
}
