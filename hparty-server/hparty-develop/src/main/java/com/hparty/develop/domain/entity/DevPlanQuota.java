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
import java.time.LocalDateTime;

/**
 * 发展党员计划指标分解（计划 → 下级组织）。
 *
 * <p>该表只有 {@code create_time} 一个审计列，因此**不继承 {@code BaseEntity}**，
 * 自行声明 create_time —— 见 {@code docs/03-数据库设计.md} 2.1。</p>
 */
@Data
@TableName("dev_plan_quota")
public class DevPlanQuota implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 指标ID */
    @TableId(value = "quota_id", type = IdType.AUTO)
    private Long quotaId;

    /** 计划ID */
    private Long planId;

    /** 被分配的组织 */
    private Long orgId;

    /** 分配名额 */
    private Integer quotaCount;

    /** 备注 */
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
