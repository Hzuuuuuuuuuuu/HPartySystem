package com.hparty.system.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 我的待办聚合结果。
 *
 * <pre>{@code
 * {
 *   "total": 5,
 *   "overdueTotal": 1,
 *   "groups": [ { "type": "DEVELOP", "typeLabel": "发展党员", "count": 2, "items": [ ... ] } ]
 * }
 * }</pre>
 */
@Data
public class TodoResultVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 待办总条数 */
    private Integer total = 0;

    /** 其中已超期的条数（首页红点用） */
    private Integer overdueTotal = 0;

    /** 分组明细，按 DEVELOP → TASK → DUES → TRANSFER 排列 */
    private List<TodoGroupVO> groups = new ArrayList<>();
}
