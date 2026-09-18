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
 * 人员统一档案
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("party_person")
public class PartyPerson extends BaseEntity {

    /** 人员ID */
    @TableId(value = "person_id", type = IdType.AUTO)
    private Long personId;

    /** 人员编号 */
    private String personNo;

    /** 姓名 */
    private String name;

    /** 性别：1=男 2=女 */
    private Integer sex;

    /** 身份证号 */
    private String idCard;

    /** 出生日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthDate;

    /** 年龄（冗余计算） */
    private Integer age;

    /** 民族 */
    private String nation;

    /** 籍贯 */
    private String nativePlace;

    /** 联系电话 */
    private String phone;

    /** 学历 */
    private String education;

    /** 工作单位 */
    private String workUnit;

    /** 职务 */
    private String jobTitle;

    /** 所属党组织 */
    private Long orgId;

    /** 所属党小组 */
    private Long groupId;

    /** 头像URL */
    private String avatar;

    /** 人员状态：0=群众 1=入党申请人 2=入党积极分子 3=发展对象 4=预备党员 5=正式党员 6=流动党员 */
    private Integer memberStatus;

    /** 政治面貌 */
    private String politicalStatus;

    /** 是否党员：0=否 1=是 */
    private Integer isMember;

    /** 递交入党申请书日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate applyDate;

    /** 确定为入党积极分子日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate activistDate;

    /** 确定为发展对象日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate candidateDate;

    /** 成为预备党员日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate probationaryDate;

    /** 转为正式党员日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fullMemberDate;

    /** 党龄（年，冗余计算） */
    private Integer partyAge;

    /** 状态：0=停用 1=正常 */
    private Integer status;

    /** 备注 */
    private String remark;
}
