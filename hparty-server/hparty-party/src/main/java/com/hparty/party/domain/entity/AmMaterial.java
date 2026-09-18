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
import java.time.LocalDateTime;

/**
 * 会议材料
 */
@Data
@TableName("am_material")
public class AmMaterial implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 材料ID */
    @TableId(value = "material_id", type = IdType.AUTO)
    private Long materialId;

    /** 会议ID */
    private Long meetingId;

    /** 组织ID */
    private Long orgId;

    /** 材料分类：NOTICE=通知 PRE_STUDY=会前学习 RECORD=记录 ANALYSIS=党员剖析材料 SELF_EVAL=党员自评材料 OTHER=其它内容 PROBLEM_LIST=问题清单 RECTIFY_LIST=整改清单 MEETING_MINUTES=会议记录 DEMOCRATIC_EVAL=民主评议党员 SITUATION_REPORT=情况报告 */
    private String category;

    /** 材料标题 */
    private String title;

    /** 文本内容 */
    private String content;

    /** 文件ID */
    private Long fileId;

    /** 文件URL */
    private String fileUrl;

    /** 上传人 person_id */
    private Long uploadBy;

    /** 上传人姓名 */
    private String uploadName;

    /** 逻辑删除标志：0=存在 1=删除 */
    @TableLogic
    @TableField(value = "del_flag", select = false)
    private Integer delFlag;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
