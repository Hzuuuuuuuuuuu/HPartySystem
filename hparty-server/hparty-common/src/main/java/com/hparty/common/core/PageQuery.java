package com.hparty.common.core;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分页查询基类。所有列表查询 DTO 继承此类。
 */
@Data
public class PageQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 默认页码 */
    public static final int DEFAULT_PAGE_NUM = 1;
    /** 默认每页条数 */
    public static final int DEFAULT_PAGE_SIZE = 10;
    /** 单页最大条数，防止恶意拉取 */
    public static final int MAX_PAGE_SIZE = 500;

    /** 页码，从 1 开始 */
    private Integer pageNum = DEFAULT_PAGE_NUM;

    /** 每页条数 */
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    /** 排序字段（下划线命名，需在白名单内） */
    private String orderByColumn;

    /** 排序方向：asc / desc */
    private String isAsc = "desc";

    public Integer getPageNum() {
        return pageNum == null || pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
    }

    public Integer getPageSize() {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
