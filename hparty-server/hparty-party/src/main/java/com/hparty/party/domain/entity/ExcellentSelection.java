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
 * 先优评选活动
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("excellent_selection")
public class ExcellentSelection extends BaseEntity {

    /** 评选ID */
    @TableId(value = "selection_id", type = IdType.AUTO)
    private Long selectionId;

    /** 评选活动名称 */
    private String title;

    /** 类型：1=优秀共产党员 2=优秀党务工作者 3=先进基层党组织 */
    private Integer selectionType;

    /** 主办组织 */
    private Long orgId;

    /** 评选年度 */
    private Integer selectionYear;

    /** 推荐开始日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /** 推荐截止日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    /** 表彰名额 */
    private Integer quota;

    /** 状态：0=草稿 1=推荐中 2=评审中 3=已公示 4=已表彰 */
    private Integer status;

    /** 评选条件与说明 */
    private String description;

    /** 通知文件ID */
    private Long fileId;

    /** 通知文件URL */
    private String fileUrl;

    /** 备注 */
    private String remark;
}
