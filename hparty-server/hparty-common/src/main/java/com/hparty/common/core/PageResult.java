package com.hparty.common.core;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 分页结果。
 *
 * @param <T> 记录类型
 */
@Data
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前页数据 */
    private List<T> records;

    /** 总记录数 */
    private long total;

    /** 当前页码 */
    private long current;

    /** 每页条数 */
    private long size;

    /** 总页数 */
    private long pages;

    public PageResult() {
        this.records = Collections.emptyList();
    }

    public PageResult(List<T> records, long total, long current, long size) {
        this.records = records == null ? Collections.emptyList() : records;
        this.total = total;
        this.current = current;
        this.size = size;
        this.pages = size > 0 ? (total + size - 1) / size : 0;
    }

    public static <T> PageResult<T> empty() {
        return new PageResult<>();
    }

    /** 直接包装 MyBatis-Plus 分页对象 */
    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 包装并转换记录类型 */
    public static <E, T> PageResult<T> of(IPage<E> page, Function<E, T> mapper) {
        List<T> list = page.getRecords().stream().map(mapper).toList();
        return new PageResult<>(list, page.getTotal(), page.getCurrent(), page.getSize());
    }
}
