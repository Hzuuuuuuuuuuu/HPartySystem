package com.hparty.system.report.dto;

import com.hparty.common.core.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 发展党员进度表导出条件（与发展党员列表页的筛选项一致）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReportDevelopQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 所属党组织 */
    private Long orgId;

    /** 当前阶段编码 */
    private String currentStage;

    /** 当前步骤编码 */
    private String currentStep;

    /** 状态：1=进行中 2=已完成 3=已终止 4=已中止 */
    private Integer status;

    /** 姓名关键字（先按姓名查出 person_id 再收敛，与列表页口径一致） */
    private String keyword;
}
