package com.hparty.system.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 党内职务任职记录
 */
@Data
@TableName("party_position")
public class PartyPosition implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "position_id", type = IdType.AUTO)
    private Long positionId;

    /** 人员ID */
    private Long personId;

    /** 组织ID */
    private Long orgId;

    /** 职务编码：SECRETARY/DEPUTY/ORG_COMMITTEE/PROP_COMMITTEE/DISC_COMMITTEE/GROUP_LEADER */
    private String positionCode;

    /** 职务名称：书记/副书记/组织委员/宣传委员/纪检委员/党小组长 */
    private String positionName;

    /** 任职开始 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /** 任职结束（NULL=在任） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    /** 是否现任：0=否 1=是 */
    private Integer isCurrent;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
