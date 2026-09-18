package com.hparty.party.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.hparty.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 党费缴纳记录（一人一月一条）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("party_dues_record")
public class PartyDuesRecord extends BaseEntity {

    /** 党费ID */
    @TableId(value = "dues_id", type = IdType.AUTO)
    private Long duesId;

    /** 人员ID */
    private Long personId;

    /** 姓名（冗余） */
    private String personName;

    /** 所属党组织 */
    private Long orgId;

    /** 年份 */
    private Integer duesYear;

    /** 月份 1-12 */
    private Integer duesMonth;

    /** 缴纳基数（月工资收入） */
    private BigDecimal duesBase;

    /** 应缴金额 */
    private BigDecimal duesStandard;

    /** 实缴金额 */
    private BigDecimal duesPaid;

    /** 缴纳日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate payDate;

    /** 缴纳方式：1=现金 2=银行代扣 3=微信 4=支付宝 5=其它 */
    private Integer payType;

    /** 状态：0=未缴 1=已缴 2=免缴 3=补缴 */
    private Integer status;

    /** 是否欠缴：0=否 1=是 */
    private Integer isOverdue;

    /** 备注 */
    private String remark;
}
