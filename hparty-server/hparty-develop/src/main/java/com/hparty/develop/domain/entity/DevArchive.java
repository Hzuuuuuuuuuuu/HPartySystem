package com.hparty.develop.domain.entity;

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
 * 发展党员材料归档
 */
@Data
@TableName("dev_archive")
public class DevArchive implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 归档ID */
    @TableId(value = "archive_id", type = IdType.AUTO)
    private Long archiveId;

    /** 申请人实例ID */
    private Long applicantId;

    /** 人员ID */
    private Long personId;

    /** 归档方式：1=存入人事档案 2=建立党员档案 */
    private Integer archiveType;

    /** 存放地点（所在党委/县级党委组织部门） */
    private String archiveLocation;

    /** 归档日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate archiveDate;

    /** 归档材料清单（JSON数组） */
    private String items;

    /** 保管人 */
    private String keeperName;

    /** 备注 */
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
