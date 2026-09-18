package com.hparty.system.domain.dto;

import com.hparty.system.domain.entity.PartyMemberProfile;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 人员档案新增/修改入参。
 * <p>一张表打通「群众 → 入党申请人 → 入党积极分子 → 发展对象 → 预备党员 → 正式党员」，
 * 因此除基础信息外还允许一并提交党员扩展信息（{@link #memberProfile}）。</p>
 */
@Data
public class PartyPersonDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 人员ID，新增时为空，修改时必填 */
    private Long personId;

    /** 人员编号，为空时由库中默认值处理 */
    @Size(max = 64, message = "人员编号长度不能超过 64")
    private String personNo;

    /** 姓名 */
    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名长度不能超过 64")
    private String name;

    /** 性别：1=男 2=女 */
    private Integer sex;

    /** 身份证号，非空时全局唯一 */
    @Size(max = 32, message = "身份证号长度不能超过 32")
    private String idCard;

    /** 出生日期，填写后自动计算年龄 */
    private LocalDate birthDate;

    /** 年龄，不填时由出生日期自动计算 */
    private Integer age;

    /** 民族 */
    @Size(max = 20, message = "民族长度不能超过 20")
    private String nation;

    /** 籍贯 */
    @Size(max = 100, message = "籍贯长度不能超过 100")
    private String nativePlace;

    /** 联系电话 */
    @Size(max = 20, message = "联系电话长度不能超过 20")
    private String phone;

    /** 学历 */
    @Size(max = 30, message = "学历长度不能超过 30")
    private String education;

    /** 工作单位 */
    @Size(max = 150, message = "工作单位长度不能超过 150")
    private String workUnit;

    /** 职务 */
    @Size(max = 100, message = "职务长度不能超过 100")
    private String jobTitle;

    /** 所属党组织 */
    @NotNull(message = "所属党组织不能为空")
    private Long orgId;

    /** 所属党小组 */
    private Long groupId;

    /** 头像URL */
    @Size(max = 500, message = "头像地址长度不能超过 500")
    private String avatar;

    /** 人员状态：0=群众 1=入党申请人 2=入党积极分子 3=发展对象 4=预备党员 5=正式党员 6=流动党员 */
    private Integer memberStatus;

    /** 政治面貌 */
    @Size(max = 20, message = "政治面貌长度不能超过 20")
    private String politicalStatus;

    /** 是否党员：0=否 1=是，不填时根据人员状态自动推导 */
    private Integer isMember;

    /** 递交入党申请书日期 */
    private LocalDate applyDate;

    /** 确定为入党积极分子日期 */
    private LocalDate activistDate;

    /** 确定为发展对象日期 */
    private LocalDate candidateDate;

    /** 成为预备党员日期 */
    private LocalDate probationaryDate;

    /** 转为正式党员日期，填写后自动计算党龄 */
    private LocalDate fullMemberDate;

    /** 党龄（年），不填时由转正日期自动计算 */
    private Integer partyAge;

    /** 状态：0=停用 1=正常 */
    private Integer status;

    /** 备注 */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

    /** 党员扩展信息，非党员可不传；传入时按人员ID执行新增或更新 */
    private PartyMemberProfile memberProfile;
}
