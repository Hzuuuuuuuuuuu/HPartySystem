package com.hparty.framework.core;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hparty.common.core.PageQuery;
import com.hparty.common.core.PageResult;

import java.util.function.Function;

/**
 * 分页工具：连接 {@link PageQuery} 与 MyBatis-Plus 的 {@link Page}。
 */
public final class PageUtils {

    private PageUtils() {
    }

    /** 由查询 DTO 构造 MyBatis-Plus 分页对象 */
    public static <T> Page<T> toPage(PageQuery query) {
        return new Page<>(query.getPageNum(), query.getPageSize());
    }

    /** 构造一个不分页的 Page（用于导出全部数据的场景） */
    public static <T> Page<T> unlimited() {
        return new Page<>(1, PageQuery.MAX_PAGE_SIZE);
    }

    /**
     * 应用排序。仅接受下划线/字母数字组成的列名，防止 SQL 注入。
     */
    public static <T> void applyOrder(QueryWrapper<T> wrapper, PageQuery query) {
        String column = sanitize(query.getOrderByColumn());
        if (column == null) {
            return;
        }
        boolean asc = "asc".equalsIgnoreCase(query.getIsAsc());
        wrapper.orderBy(true, asc, column);
    }

    /** 列名白名单校验：只允许字母、数字、下划线 */
    private static String sanitize(String column) {
        if (column == null || column.isBlank()) {
            return null;
        }
        String trimmed = column.trim();
        if (!trimmed.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return null;
        }
        // 驼峰转下划线
        return com.baomidou.mybatisplus.core.toolkit.StringUtils.camelToUnderline(trimmed);
    }

    /** 分页结果转换 */
    public static <E, T> PageResult<T> convert(Page<E> page, Function<E, T> mapper) {
        return PageResult.of(page, mapper);
    }
}
