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
 * 党组织换届选举记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("org_election")
public class OrgElection extends BaseEntity {

    /** 换届ID */
    @TableId(value = "election_id", type = IdType.AUTO)
    private Long electionId;

    /** 换届组织 */
    private Long orgId;

    /** 类型：1=换届选举 2=补选 3=委员调整 */
    private Integer electionType;

    /** 届次，如 5 表示第五届 */
    private Integer termNo;

    /** 换届名称 */
    private String title;

    /** 换届事由（任期届满/委员缺额等） */
    private String reason;

    /** 计划换届日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate planDate;

    /** 实际选举日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate electionDate;

    /** 会议地点 */
    private String place;

    /** 主持人 person_id */
    private Long hostId;

    /** 主持人姓名 */
    private String hostName;

    /** 记录人 */
    private String recorderName;

    /** 应到有选举权党员数 */
    private Integer shouldAttend;

    /** 实到有选举权党员数 */
    private Integer actualAttend;

    /** 开会法定人数 */
    private Integer quorumRequired;

    /** 是否达到法定人数：0=否 1=是 */
    private Integer isQuorumMet;

    /** 状态：0=筹备中 1=进行中 2=已完成 3=已终止 */
    private Integer status;

    /** 选举结果摘要 */
    private String resultSummary;

    /** 批准组织（上级党委） */
    private Long approveOrgId;

    /** 批复日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate approveDate;

    /** 请示/批复文件ID */
    private Long fileId;

    /** 文件URL */
    private String fileUrl;

    /** 备注 */
    private String remark;
}
