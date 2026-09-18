package com.hparty.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.hparty.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 党组织
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
public class SysDept extends BaseEntity {

    /** 组织ID */
    @TableId(value = "org_id", type = IdType.AUTO)
    private Long orgId;

    /** 父组织ID，0=根 */
    private Long parentId;

    /** 祖级列表，逗号分隔：0,1,3 */
    private String ancestors;

    /** 物化路径：/1/3/7/ */
    private String orgPath;

    /** 组织名称 */
    private String orgName;

    /** 组织简称 */
    private String orgShortName;

    /** 组织编码 */
    private String orgCode;

    /** 组织类型：1=党委 2=党总支 3=党支部 4=党小组 */
    private Integer orgType;

    /** 层级：1=党委 2=党总支 3=党支部 4=党小组 */
    private Integer orgLevel;

    /** 书记（party_person.person_id） */
    private Long secretaryId;

    /** 副书记 */
    private Long deputyId;

    /** 组织委员 */
    private Long orgCommitteeId;

    /** 宣传委员 */
    private Long propCommitteeId;

    /** 纪检委员 */
    private Long discCommitteeId;

    /** 成立日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate foundedDate;

    /** 党员数（冗余） */
    private Integer memberCount;

    /** 显示排序 */
    private Integer orderNum;

    /** 负责人姓名（冗余） */
    private String leader;

    /** 联系电话 */
    private String phone;

    /** 办公地址 */
    private String address;

    /** 状态：0=停用 1=正常 */
    private Integer status;
}
