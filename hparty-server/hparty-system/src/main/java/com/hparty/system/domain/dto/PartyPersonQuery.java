package com.hparty.system.domain.dto;

import com.hparty.common.core.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 人员档案查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PartyPersonQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 姓名（模糊匹配） */
    private String name;

    /** 联系电话（模糊匹配） */
    private String phone;

    /** 身份证号（模糊匹配） */
    private String idCard;

    /** 所属党组织（精确匹配） */
    private Long orgId;

    /** 所属党小组（精确匹配） */
    private Long groupId;

    /** 人员状态：0=群众 1=入党申请人 2=入党积极分子 3=发展对象 4=预备党员 5=正式党员 6=流动党员 */
    private Integer memberStatus;

    /** 政治面貌（精确匹配） */
    private String politicalStatus;

    /** 是否党员：0=否 1=是 */
    private Integer isMember;

    /** 状态：0=停用 1=正常 */
    private Integer status;
}
