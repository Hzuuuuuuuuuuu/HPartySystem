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
 * 换届候选人与当选情况
 * <p>该表只有 {@code create_time} 一个审计列，故不继承 {@code BaseEntity}。</p>
 */
@Data
@TableName("org_election_candidate")
public class OrgElectionCandidate implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "candidate_id", type = IdType.AUTO)
    private Long candidateId;

    /** 换届ID */
    private Long electionId;

    /** 人员ID */
    private Long personId;

    /** 姓名（冗余） */
    private String personName;

    /** 候选职务编码：SECRETARY/DEPUTY/ORG_COMMITTEE/PROP_COMMITTEE/DISC_COMMITTEE */
    private String positionCode;

    /** 候选职务名称 */
    private String positionName;

    /** 是否现任：0=否 1=是 */
    private Integer isIncumbent;

    /** 得票数 */
    private Integer votes;

    /** 是否当选：0=否 1=是 */
    private Integer isElected;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
