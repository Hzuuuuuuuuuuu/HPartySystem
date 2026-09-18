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
import java.time.LocalDateTime;

/**
 * 先优评选候选人
 * <p>该表只有 {@code create_time} 一个审计列，故不继承 {@code BaseEntity}。</p>
 */
@Data
@TableName("excellent_candidate")
public class ExcellentCandidate implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "candidate_id", type = IdType.AUTO)
    private Long candidateId;

    /** 评选ID */
    private Long selectionId;

    /** 候选人 person_id（个人类评选） */
    private Long personId;

    /** 候选人姓名 */
    private String personName;

    /** 候选组织ID（组织类评选） */
    private Long orgId;

    /** 候选组织名称 */
    private String orgName;

    /** 推荐组织 */
    private Long recommendOrgId;

    /** 主要事迹 */
    private String deeds;

    /** 得票数 */
    private Integer votes;

    /** 排名 */
    private Integer rankNo;

    /** 结果：0=待评审 1=已推荐 2=已获奖 3=未获奖 */
    private Integer result;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
