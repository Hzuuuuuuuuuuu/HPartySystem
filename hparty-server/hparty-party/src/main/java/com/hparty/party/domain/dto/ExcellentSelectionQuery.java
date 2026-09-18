package com.hparty.party.domain.dto;

import com.hparty.common.core.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 先优评选活动查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ExcellentSelectionQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主办组织（精确匹配） */
    private Long orgId;

    /** 类型：1=优秀共产党员 2=优秀党务工作者 3=先进基层党组织 */
    private Integer selectionType;

    /** 评选年度 */
    private Integer selectionYear;

    /** 状态：0=草稿 1=推荐中 2=评审中 3=已公示 4=已表彰 */
    private Integer status;
}
