package com.hparty.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hparty.common.constant.Constants;
import com.hparty.common.core.PageResult;
import com.hparty.common.exception.BizException;
import com.hparty.framework.core.PageUtils;
import com.hparty.system.domain.dto.SysDictTypeQuery;
import com.hparty.system.domain.entity.SysDictData;
import com.hparty.system.domain.entity.SysDictType;
import com.hparty.system.mapper.SysDictDataMapper;
import com.hparty.system.mapper.SysDictTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 字典类型服务。
 * <p>字典类型是字典数据的归属，因此：</p>
 * <ul>
 *   <li>{@code dict_type} 唯一，新增/修改时校验；</li>
 *   <li>类型下已存在字典数据时，不允许删除类型，也不允许修改类型编码（否则数据会失去归属）；</li>
 *   <li>类型发生任何变更后，同步失效该类型对应的字典缓存。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SysDictTypeService {

    private final SysDictTypeMapper dictTypeMapper;

    private final SysDictDataMapper dictDataMapper;

    private final SysDictDataService dictDataService;

    /**
     * 分页查询字典类型。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    public PageResult<SysDictType> page(SysDictTypeQuery query) {
        QueryWrapper<SysDictType> wrapper = new QueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getDictName()), "dict_name", query.getDictName());
        wrapper.like(StringUtils.hasText(query.getDictType()), "dict_type", query.getDictType());
        wrapper.eq(query.getStatus() != null, "status", query.getStatus());
        if (StringUtils.hasText(query.getOrderByColumn())) {
            PageUtils.applyOrder(wrapper, query);
        } else {
            wrapper.orderByDesc("dict_id");
        }
        return PageResult.of(dictTypeMapper.selectPage(PageUtils.toPage(query), wrapper));
    }

    /**
     * 字典类型详情。
     *
     * @param dictId 字典主键
     * @return 字典类型
     */
    public SysDictType getDetail(Long dictId) {
        BizException.throwIf(dictId == null, "字典主键不能为空");
        SysDictType entity = dictTypeMapper.selectById(dictId);
        BizException.throwIf(entity == null, "字典类型不存在");
        return entity;
    }

    /**
     * 按字典类型编码查询（唯一）。
     *
     * @param dictType 字典类型编码
     * @return 字典类型，不存在时返回 null
     */
    public SysDictType getByType(String dictType) {
        if (!StringUtils.hasText(dictType)) {
            return null;
        }
        QueryWrapper<SysDictType> wrapper = new QueryWrapper<>();
        wrapper.eq("dict_type", dictType);
        List<SysDictType> list = dictTypeMapper.selectList(wrapper);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 新增字典类型。
     * <p>校验字典类型编码唯一。</p>
     *
     * @param dictType 字典类型
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean add(SysDictType dictType) {
        BizException.throwIf(dictType == null, "字典类型不能为空");
        BizException.throwIf(!StringUtils.hasText(dictType.getDictName()), "字典名称不能为空");
        BizException.throwIf(!StringUtils.hasText(dictType.getDictType()), "字典类型不能为空");
        BizException.throwIf(getByType(dictType.getDictType()) != null, "字典类型已存在：" + dictType.getDictType());

        dictType.setDictId(null);
        if (dictType.getStatus() == null) {
            dictType.setStatus(Constants.STATUS_NORMAL);
        }
        boolean success = dictTypeMapper.insert(dictType) > 0;
        if (success) {
            dictDataService.evictCache(dictType.getDictType());
        }
        return success;
    }

    /**
     * 修改字典类型。
     * <p>校验：类型存在；类型编码改动时需唯一，且该类型下不能已有字典数据。</p>
     *
     * @param dictType 字典类型，dictId 必填
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean update(SysDictType dictType) {
        BizException.throwIf(dictType == null || dictType.getDictId() == null, "字典主键不能为空");
        SysDictType old = dictTypeMapper.selectById(dictType.getDictId());
        BizException.throwIf(old == null, "字典类型不存在");
        BizException.throwIf(!StringUtils.hasText(dictType.getDictName()), "字典名称不能为空");
        BizException.throwIf(!StringUtils.hasText(dictType.getDictType()), "字典类型不能为空");

        String newType = dictType.getDictType();
        if (!newType.equals(old.getDictType())) {
            BizException.throwIf(countDataByType(old.getDictType()) > 0,
                    "该字典类型下已存在字典数据，不允许修改字典类型编码");
            SysDictType exist = getByType(newType);
            BizException.throwIf(exist != null && !exist.getDictId().equals(old.getDictId()),
                    "字典类型已存在：" + newType);
        }

        boolean success = dictTypeMapper.updateById(dictType) > 0;
        if (success) {
            dictDataService.evictCache(old.getDictType());
            dictDataService.evictCache(newType);
        }
        return success;
    }

    /**
     * 删除字典类型。
     * <p>校验：该类型下不能存在字典数据。</p>
     *
     * @param dictId 字典主键
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long dictId) {
        BizException.throwIf(dictId == null, "字典主键不能为空");
        SysDictType old = dictTypeMapper.selectById(dictId);
        BizException.throwIf(old == null, "字典类型不存在");
        BizException.throwIf(countDataByType(old.getDictType()) > 0,
                "该字典类型下存在字典数据，不允许删除");

        boolean success = dictTypeMapper.deleteById(dictId) > 0;
        if (success) {
            dictDataService.evictCache(old.getDictType());
        }
        return success;
    }

    /** 统计某字典类型下的字典项数量 */
    private long countDataByType(String dictType) {
        QueryWrapper<SysDictData> wrapper = new QueryWrapper<>();
        wrapper.eq("dict_type", dictType);
        return dictDataMapper.selectCount(wrapper);
    }
}
