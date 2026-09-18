package com.hparty.system.domain.vo;

import com.hparty.system.domain.entity.SysDictData;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 字典数据展示对象。
 * <p>该对象会被放入字典缓存（key 前缀 {@code hparty:dict:}），
 * 因此字段保持精简、不可变使用（查询结果一律以只读列表返回）。</p>
 */
@Data
public class SysDictDataVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 字典编码 */
    private Long dictCode;

    /** 字典排序 */
    private Integer dictSort;

    /** 字典标签 */
    private String dictLabel;

    /** 字典键值 */
    private String dictValue;

    /** 字典类型 */
    private String dictType;

    /** 样式属性 */
    private String cssClass;

    /** 表格回显样式 */
    private String listClass;

    /** 是否默认：0=否 1=是 */
    private Integer isDefault;

    /** 状态：0=停用 1=正常 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 由实体转换 */
    public static SysDictDataVO of(SysDictData entity) {
        if (entity == null) {
            return null;
        }
        SysDictDataVO vo = new SysDictDataVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
