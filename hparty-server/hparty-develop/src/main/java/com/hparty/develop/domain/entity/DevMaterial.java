package com.hparty.develop.domain.entity;

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
 * 发展党员材料档案
 */
@Data
@TableName("dev_material")
public class DevMaterial implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 材料ID */
    @TableId(value = "material_id", type = IdType.AUTO)
    private Long materialId;

    /** 申请人实例ID */
    private Long applicantId;

    /** 人员ID */
    private Long personId;

    /** 关联步骤编码 */
    private String stepCode;

    /** 材料类型：APPLY_BOOK=入党申请书 THOUGHT_REPORT=思想汇报 POLITICAL_REVIEW=政治审查材料 VOLUNTEER_BOOK=入党志愿书 REGULAR_APPLY=转正申请书 TRAINING_CERT=培训证明 OTHER=其它 */
    private String materialType;

    /**
     * 对应 {@code dev_material_template.template_code}（如 {@code 1-1}、{@code 4-3}）。
     * <p>用于精确判定「这份材料 fulfills 哪份模板」。留空时退化为按
     * {@code material_type + step_code} 宽松匹配 —— 那会导致同一步骤上的
     * 多份同类型材料（如 STEP_23 上的 5 份 OTHER）相互误判为已上传。</p>
     */
    private String templateCode;

    /** 材料名称 */
    private String materialName;

    /** 文件ID（sys_file） */
    private Long fileId;

    /** 文件URL */
    private String fileUrl;

    /** 文本内容（如思想汇报正文） */
    private String content;

    /** 提交日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate submitDate;

    /** 是否必备材料 */
    private Integer isRequired;

    /** 逻辑删除标志：0=存在 1=删除 */
    @TableLogic
    @TableField(value = "del_flag", select = false)
    private Integer delFlag;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
