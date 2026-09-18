package com.hparty.system.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hparty.common.enums.MemberStatus;
import com.hparty.system.domain.entity.PartyPerson;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 人员档案列表展示对象。
 * <p>{@code orgName} / {@code groupName} 由 Service 批量补全，避免 N+1 查询。</p>
 */
@Data
public class PartyPersonVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 人员ID */
    private Long personId;

    /** 人员编号 */
    private String personNo;

    /** 姓名 */
    private String name;

    /** 性别：1=男 2=女 */
    private Integer sex;

    /** 性别文本 */
    private String sexLabel;

    /** 身份证号 */
    private String idCard;

    /** 出生日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthDate;

    /** 年龄 */
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

    /** 所属党组织名称 */
    private String orgName;

    /** 所属党小组 */
    private Long groupId;

    /** 所属党小组名称 */
    private String groupName;

    /** 头像URL */
    private String avatar;

    /** 人员状态：0=群众 1=入党申请人 2=入党积极分子 3=发展对象 4=预备党员 5=正式党员 6=流动党员 */
    private Integer memberStatus;

    /** 人员状态文本 */
    private String memberStatusLabel;

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

    /** 党龄（年） */
    private Integer partyAge;

    /** 状态：0=停用 1=正常 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 由实体转换（组织名称等关联字段由 Service 补全） */
    public static PartyPersonVO of(PartyPerson entity) {
        if (entity == null) {
            return null;
        }
        PartyPersonVO vo = new PartyPersonVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setSexLabel(sexLabel(entity.getSex()));
        vo.setMemberStatusLabel(MemberStatus.labelOf(entity.getMemberStatus()));
        return vo;
    }

    /** 性别文本 */
    public static String sexLabel(Integer sex) {
        if (sex == null) {
            return "";
        }
        return switch (sex) {
            case 1 -> "男";
            case 2 -> "女";
            default -> "未知";
        };
    }
}
