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
 * 党纪学习教育
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("discipline_study")
public class DisciplineStudy extends BaseEntity {

    /** 党纪学习ID */
    @TableId(value = "study_id", type = IdType.AUTO)
    private Long studyId;

    /** 学习主题 */
    private String title;

    /** 类型：1=条例学习 2=警示教育 3=专题党课 4=知识测试 5=案例研讨 */
    private Integer studyType;

    /** 组织ID */
    private Long orgId;

    /** 学习日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate studyDate;

    /** 学习地点 */
    private String place;

    /** 主讲人 */
    private String teacher;

    /** 学习内容 */
    private String content;

    /** 参加人数 */
    private Integer participantCount;

    /** 测试通过人数 */
    private Integer passCount;

    /** 状态：0=草稿 1=进行中 2=已结束 */
    private Integer status;

    /** 学习材料文件ID */
    private Long fileId;

    /** 学习材料URL */
    private String fileUrl;

    /** 备注 */
    private String remark;
}
