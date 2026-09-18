package com.hparty.system.domain.dto;

import com.hparty.common.core.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 字典类型查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysDictTypeQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 字典名称（模糊匹配） */
    private String dictName;

    /** 字典类型（模糊匹配） */
    private String dictType;

    /** 状态：0=停用 1=正常，为空表示不限 */
    private Integer status;
}
