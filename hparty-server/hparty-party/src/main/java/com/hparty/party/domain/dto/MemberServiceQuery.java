package com.hparty.party.domain.dto;

import com.hparty.common.core.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 党员服务查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MemberServiceQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 受理组织（精确匹配） */
    private Long orgId;

    /** 类型：1=困难帮扶 2=志愿服务 3=走访慰问 4=权益维护 5=就业帮扶 6=其它 */
    private Integer serviceType;

    /** 状态：0=待处理 1=处理中 2=已完成 3=已取消 */
    private Integer status;

    /** 服务对象姓名（模糊匹配） */
    private String personName;

    /** 关键词：服务事项 / 服务内容模糊匹配 */
    private String keyword;
}
