package com.hparty.system.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 导出专用的 {@code party_dues_record} 最小投影。
 *
 * <p>统计报表要读各业务域的数据，但 {@code hparty-system} 不依赖
 * {@code hparty-party} / {@code hparty-develop}。与其引入整个模块，
 * 不如像 {@code PartyFile} 映射 {@code sys_file} 那样，用最小字段集投影到目标表 ——
 * 这样列表查询的 {@code LambdaQueryWrapper} 与 {@code DataScopeHelper} 都能直接复用，
 * 数据权限与列表页天然是同一套条件。</p>
 *
 * <p>只读用途，字段按导出所需声明；{@code delFlag} 打 {@code @TableLogic} 让
 * MyBatis-Plus 自动追加 {@code del_flag = 0}。</p>
 */
@Data
@TableName("party_dues_record")
public class ReportDuesRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
    private LocalDate payDate;

    /** 缴纳方式：1=现金 2=银行代扣 3=微信 4=支付宝 5=其它 */
    private Integer payType;

    /** 状态：0=未缴 1=已缴 2=免缴 3=补缴 */
    private Integer status;

    /** 是否欠缴：0=否 1=是 */
    private Integer isOverdue;

    /** 删除标志 */
    @TableLogic
    private Integer delFlag;
}
