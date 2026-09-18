package com.hparty.party.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.hparty.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 组织关系转接单。
 *
 * <p>对应 {@code party_transfer} 表，审计列 5 个齐全（create_by / create_time /
 * update_by / update_time / del_flag），因此继承 {@link BaseEntity}。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("party_transfer")
public class PartyTransfer extends BaseEntity {

    /** 转接ID */
    @TableId(value = "transfer_id", type = IdType.AUTO)
    private Long transferId;

    /** 转接单号，如 ZZ-2026-0001 */
    private String transferNo;

    /** 类型：1=转出 2=转入 3=内部调整 */
    private Integer transferType;

    /** 党员 person_id */
    private Long personId;

    /** 姓名（冗余） */
    private String personName;

    /** 原党组织（转出时必填） */
    private Long fromOrgId;

    /** 原党组织名称（冗余） */
    private String fromOrgName;

    /** 目标党组织（转入时必填） */
    private Long toOrgId;

    /** 目标党组织名称（冗余） */
    private String toOrgName;

    /** 转接事由 */
    private String reason;

    /** 介绍信号 */
    private String letterNo;

    /** 介绍信开具日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate letterDate;

    /** 有效期天数 */
    private Integer validDays;

    /** 失效日期 = 开具日期 + 有效期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expireDate;

    /** 状态：0=待提交 1=已开具(待接收) 2=已接收 3=已拒绝 4=已超期 5=已撤销 */
    private Integer status;

    /** 实际转接完成日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate transferDate;

    /** 经办人 person_id */
    private Long handlerId;

    /** 经办人姓名（冗余） */
    private String handlerName;

    /** 拒绝原因 */
    private String rejectReason;

    /** 介绍信扫描件 */
    private Long fileId;

    /** 介绍信扫描件URL */
    private String fileUrl;

    /** 备注 */
    private String remark;
}
