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
 * 党员服务记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("member_service")
public class MemberService extends BaseEntity {

    /** 服务ID */
    @TableId(value = "service_id", type = IdType.AUTO)
    private Long serviceId;

    /** 服务事项 */
    private String title;

    /** 类型：1=困难帮扶 2=志愿服务 3=走访慰问 4=权益维护 5=就业帮扶 6=其它 */
    private Integer serviceType;

    /** 服务对象 person_id */
    private Long personId;

    /** 服务对象姓名 */
    private String personName;

    /** 受理组织 */
    private Long orgId;

    /** 服务日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate serviceDate;

    /** 服务内容 */
    private String content;

    /** 帮扶金额 */
    private BigDecimal amount;

    /** 经办人 person_id */
    private Long handlerId;

    /** 经办人姓名 */
    private String handlerName;

    /** 状态：0=待处理 1=处理中 2=已完成 3=已取消 */
    private Integer status;

    /** 办理结果 */
    private String result;

    /** 附件ID */
    private Long fileId;

    /** 附件URL */
    private String fileUrl;

    /** 备注 */
    private String remark;
}
