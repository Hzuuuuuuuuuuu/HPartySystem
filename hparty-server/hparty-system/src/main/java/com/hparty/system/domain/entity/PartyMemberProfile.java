package com.hparty.system.domain.entity;

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
 * 党员扩展信息
 */
@Data
@TableName("party_member_profile")
public class PartyMemberProfile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "profile_id", type = IdType.AUTO)
    private Long profileId;

    /** 人员ID */
    private Long personId;

    /** 支部书记姓名（冗余） */
    private String branchSecretary;

    /** 入党介绍人1 */
    private Long introducer1Id;

    /** 入党介绍人2 */
    private Long introducer2Id;

    /** 培养联系人1 */
    private Long trainer1Id;

    /** 培养联系人2 */
    private Long trainer2Id;

    /** 入党志愿书编号 */
    private String volunteerBookNo;

    /** 党费缴纳基数（元） */
    private BigDecimal duesBase;

    /** 每月应缴党费 */
    private BigDecimal duesStandard;

    /** 党内职务 */
    private String partyPosition;

    /** 是否流动党员：0=否 1=是 */
    private Integer isFlow;

    /** 流动去向 */
    private String flowLocation;

    /** 档案存放地 */
    private String archiveLocation;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
