package com.hparty.develop.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 发展阶段模板
 */
@Data
@TableName("dev_stage")
public class DevStage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 阶段ID */
    @TableId(value = "stage_id", type = IdType.AUTO)
    private Long stageId;

    /** 阶段编码：STAGE_1..STAGE_5 */
    private String stageCode;

    /** 阶段名称 */
    private String stageName;

    /** 阶段顺序：1..5 */
    private Integer stageOrder;

    /** 阶段说明 */
    private String description;
}
