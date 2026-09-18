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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 集中培训记录
 */
@Data
@TableName("dev_training")
public class DevTraining implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 培训ID */
    @TableId(value = "training_id", type = IdType.AUTO)
    private Long trainingId;

    /** 申请人实例ID */
    private Long applicantId;

    /** 培训名称 */
    private String trainingName;

    /** 主办单位：基层党委/县级党委组织部门 */
    private String organizer;

    /** 开始日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /** 结束日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    /** 培训天数（须>=3） */
    private BigDecimal trainDays;

    /** 培训学时（须>=24） */
    private BigDecimal trainHours;

    /** 是否合格：0=否 1=是 */
    private Integer isQualified;

    /** 结业证明文件ID */
    private Long certFileId;

    /** 备注 */
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
