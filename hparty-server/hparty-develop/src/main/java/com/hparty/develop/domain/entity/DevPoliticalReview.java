package com.hparty.develop.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 政治审查
 */
@Data
@TableName("dev_political_review")
public class DevPoliticalReview implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 政审ID */
    @TableId(value = "review_id", type = IdType.AUTO)
    private Long reviewId;

    /** 申请人实例ID */
    private Long applicantId;

    /** 审查日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate reviewDate;

    /** 对党的理论和路线、方针、政策的态度 */
    private String attitude;

    /** 政治历史和在重大政治斗争中的表现 */
    private String history;

    /** 遵纪守法和遵守社会公德情况 */
    private String lawAbide;

    /** 直系亲属和主要社会关系的政治情况 */
    private String relatives;

    /** 审查方法：同本人谈话/查阅档案/函调/外调 */
    private String method;

    /** 政治审查结论性材料 */
    private String conclusion;

    /** 审查结果：1=合格 2=不合格 */
    private Integer reviewResult;

    /** 审查人 */
    private Long reviewerId;

    /** 审查人姓名 */
    private String reviewerName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
