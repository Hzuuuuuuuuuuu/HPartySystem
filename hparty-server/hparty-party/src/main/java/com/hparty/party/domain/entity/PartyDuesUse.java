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
 * 党费使用记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("party_dues_use")
public class PartyDuesUse extends BaseEntity {

    /** 使用ID */
    @TableId(value = "use_id", type = IdType.AUTO)
    private Long useId;

    /** 使用组织 */
    private Long orgId;

    /** 年份 */
    private Integer useYear;

    /** 月份 */
    private Integer useMonth;

    /** 使用金额 */
    private BigDecimal amount;

    /** 用途分类：1=党员教育 2=表彰奖励 3=困难帮扶 4=阵地建设 5=订阅报刊 6=其它 */
    private Integer useCategory;

    /** 具体用途 */
    private String purpose;

    /** 使用日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate useDate;

    /** 审批人 */
    private String approver;

    /** 凭证文件ID */
    private Long fileId;

    /** 凭证URL */
    private String fileUrl;

    /** 备注 */
    private String remark;
}
