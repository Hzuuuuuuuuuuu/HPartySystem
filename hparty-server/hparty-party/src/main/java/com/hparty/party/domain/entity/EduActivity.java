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
 * 党员教育活动
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_activity")
public class EduActivity extends BaseEntity {

    /** 教育活动ID */
    @TableId(value = "activity_id", type = IdType.AUTO)
    private Long activityId;

    /** 活动名称 */
    private String title;

    /** 类型：1=党课 2=专题培训 3=在线学习 4=实践锻炼 5=集中轮训 */
    private Integer activityType;

    /** 主办党组织 */
    private Long orgId;

    /** 主办单位 */
    private String organizer;

    /** 开始日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /** 结束日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    /** 学时 */
    private BigDecimal studyHours;

    /** 活动地点 */
    private String place;

    /** 主讲人 */
    private String teacher;

    /** 活动内容 */
    private String content;

    /** 应到人数 */
    private Integer shouldAttend;

    /** 实到人数 */
    private Integer actualAttend;

    /** 状态：0=草稿 1=报名中 2=进行中 3=已结束 */
    private Integer status;

    /** 材料文件ID */
    private Long fileId;

    /** 材料URL */
    private String fileUrl;

    /** 备注 */
    private String remark;
}
