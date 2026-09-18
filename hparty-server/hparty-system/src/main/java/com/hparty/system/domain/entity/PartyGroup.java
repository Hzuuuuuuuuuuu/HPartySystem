package com.hparty.system.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 党小组
 */
@Data
@TableName("party_group")
public class PartyGroup implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 党小组ID */
    @TableId(value = "group_id", type = IdType.AUTO)
    private Long groupId;

    /** 党小组名称 */
    private String groupName;

    /** 所属党支部 */
    private Long orgId;

    /** 党小组长 */
    private Long leaderId;

    /** 人数 */
    private Integer memberCount;

    /** 显示排序 */
    private Integer orderNum;

    /** 状态：0=停用 1=正常 */
    private Integer status;

    /** 删除标志：0=存在 1=删除 */
    @TableLogic
    @TableField(value = "del_flag")
    private Integer delFlag;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
