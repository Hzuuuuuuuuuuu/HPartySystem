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
import java.time.LocalDateTime;

/**
 * 发展党员材料模板（对应《广西发展党员工作手册》50 份表格）。
 *
 * <p>表里只有 create_by/create_time/update_by/update_time 四个审计列，
 * <b>没有</b> del_flag，因此不继承 BaseEntity（见 docs/05-开发规范.md 第四节）。</p>
 */
@Data
@TableName("dev_material_template")
public class DevMaterialTemplate implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 模板ID */
    @TableId(value = "template_id", type = IdType.AUTO)
    private Long templateId;

    /** 模板编号，如 1-1、4-1a（与手册附件编号一致） */
    private String templateCode;

    /** 材料名称 */
    private String templateName;

    /** 所属阶段：STAGE_1..STAGE_5 */
    private String stageCode;

    /** 关联步骤；NULL 表示阶段通用或全程通用 */
    private String stepCode;

    /** 材料类型，对应 dev_material.material_type */
    private String materialType;

    /** 是否必备：0=否 1=是（必备材料缺失时流程规则会提示） */
    private Integer isRequired;

    /** 是否组织台账/名册：1=按组织归档，不随个人流程流转 */
    private Integer isRoster;

    /** 提交/出具方：APPLICANT=本人 BRANCH=党支部 TRAINER=培养联系人 PARENT_ORG=上级党委 */
    private String submitRole;

    /** 空白模板文件名（material-templates/blank/ 下） */
    private String blankFile;

    /** 填写样例文件名（material-templates/sample/ 下） */
    private String sampleFile;

    /** 填写说明 */
    private String fillNote;

    /** 显示顺序 */
    private Integer orderNum;

    /** 备注 */
    private String remark;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
