package com.hparty.party.domain.entity;

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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 活动任务通知
 */
@Data
@TableName("am_task")
public class AmTask implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务ID */
    @TableId(value = "task_id", type = IdType.AUTO)
    private Long taskId;

    /** 任务标题 */
    private String title;

    /** 任务类型：同 meeting_type */
    private String taskType;

    /** 发布单位组织ID */
    private Long publishOrgId;

    /** 发布单位名称（冗余） */
    private String publishOrgName;

    /** 活动名称 */
    private String activityName;

    /** 活动内容 */
    private String content;

    /** 活动开始日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /** 活动结束日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    /** 材料上传截止日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate deadline;

    /** 接收组织ID，逗号分隔（NULL=全部下辖） */
    private String receiveOrgIds;

    /** 状态：0=草稿 1=已发布 2=已截止 3=已归档 */
    private Integer status;

    /** 发布人 */
    private String publishBy;

    /** 发布时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标志：0=存在 1=删除 */
    @TableLogic
    @TableField(value = "del_flag", select = false)
    private Integer delFlag;
}
