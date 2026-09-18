package com.hparty.party.domain.dto;

import com.hparty.common.core.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDate;

/**
 * 组织关系转接查询条件。
 *
 * <p>时间范围按「介绍信开具日期」筛选，参数为 {@code yyyy-MM-dd}。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TransferQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 类型：1=转出 2=转入 3=内部调整 */
    private Integer transferType;

    /** 状态：0=待提交 1=已开具 2=已接收 3=已拒绝 4=已超期 5=已撤销 */
    private Integer status;

    /** 姓名模糊匹配 */
    private String personName;

    /** 转接单号 / 介绍信号模糊匹配 */
    private String transferNo;

    /** 原组织 */
    private Long fromOrgId;

    /** 目标组织 */
    private Long toOrgId;

    /** 开具日期起（含） */
    private LocalDate beginDate;

    /** 开具日期止（含） */
    private LocalDate endDate;
}
