package com.hparty.party.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 活动任务提交记录
 */
@Data
@TableName("am_task_submit")
public class AmTaskSubmit implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "submit_id", type = IdType.AUTO)
    private Long submitId;

    /** 任务ID */
    private Long taskId;

    /** 提交组织 */
    private Long orgId;

    /** 文件ID */
    private Long fileId;

    /** 文件URL */
    private String fileUrl;

    /** 备注 */
    private String remark;

    /** 提交人 */
    private String submitBy;

    /** 提交时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime submitTime;
}
