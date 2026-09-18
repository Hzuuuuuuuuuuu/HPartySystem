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
 * 党纪学习参与记录
 * <p>该表只有 {@code create_time} 一个审计列，故不继承 {@code BaseEntity}。</p>
 */
@Data
@TableName("discipline_participant")
public class DisciplineParticipant implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "participant_id", type = IdType.AUTO)
    private Long participantId;

    /** 党纪学习ID */
    private Long studyId;

    /** 人员ID */
    private Long personId;

    /** 姓名（冗余） */
    private String personName;

    /** 参与情况：0=未签到 1=已参加 2=请假 3=缺席 */
    private Integer attendStatus;

    /** 测试成绩 */
    private BigDecimal score;

    /** 是否通过：0=否 1=是 */
    private Integer isPassed;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
