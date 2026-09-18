package com.hparty.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hparty.common.constant.Constants;
import com.hparty.common.core.PageResult;
import com.hparty.common.exception.BizException;
import com.hparty.framework.core.PageUtils;
import com.hparty.system.domain.dto.SysDictDataQuery;
import com.hparty.system.domain.entity.SysDictData;
import com.hparty.system.domain.entity.SysDictType;
import com.hparty.system.domain.vo.SysDictDataVO;
import com.hparty.system.mapper.SysDictDataMapper;
import com.hparty.system.mapper.SysDictTypeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 字典数据服务。
 * <p>字典属于「全表高频读取的小数据」，因此按 {@code dictType} 维度做进程内缓存：
 * 缓存 key 为 {@link Constants#CACHE_DICT} + dictType，value 为该类型下的<strong>全部</strong>字典项
 * （含停用项），按 {@code dict_sort} 升序；需要「只取正常项」时在内存中过滤，避免缓存多份。</p>
 * <p>任何写操作（新增/修改/删除）都会立即失效对应 key，字典变更后不会读到旧值。</p>
 * <p>说明：当前为单机 JVM 缓存（项目尚未引入统一缓存抽象，框架也未开启 {@code @EnableCaching}），
 * 多实例部署时可平滑替换为 Redis 实现，方法签名无需变化。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysDictDataService {

    private final SysDictDataMapper dictDataMapper;

    private final SysDictTypeMapper dictTypeMapper;

    /** 字典缓存：key = hparty:dict:{dictType}，value = 该类型下的全部字典项（只读列表） */
    private final Map<String, List<SysDictDataVO>> dictCache = new ConcurrentHashMap<>();

    /**
     * 按字典类型查询字典项（含停用项），走缓存。
     *
     * @param dictType 字典类型
     * @return 按 dict_sort 升序的字典项，类型为空时返回空列表
     */
    public List<SysDictDataVO> listByType(String dictType) {
        if (!StringUtils.hasText(dictType)) {
            return List.of();
        }
        return dictCache.computeIfAbsent(cacheKey(dictType), key -> loadFromDb(dictType));
    }

    /**
     * 按字典类型查询字典项，并按状态过滤，走缓存。
     * <p>前端下拉框场景传 {@code status = 1} 只取启用项。</p>
     *
     * @param dictType 字典类型
     * @param status   状态：0=停用 1=正常，为空表示全部
     * @return 字典项列表
     */
    public List<SysDictDataVO> listByType(String dictType, Integer status) {
        List<SysDictDataVO> all = listByType(dictType);
        if (status == null) {
            return all;
        }
        return all.stream().filter(item -> status.equals(item.getStatus())).toList();
    }

    /**
     * 分页查询字典项（管理页面用，不走缓存）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    public PageResult<SysDictDataVO> page(SysDictDataQuery query) {
        QueryWrapper<SysDictData> wrapper = new QueryWrapper<>();
        wrapper.eq(StringUtils.hasText(query.getDictType()), "dict_type", query.getDictType());
        wrapper.like(StringUtils.hasText(query.getDictLabel()), "dict_label", query.getDictLabel());
        wrapper.eq(query.getStatus() != null, "status", query.getStatus());
        if (StringUtils.hasText(query.getOrderByColumn())) {
            PageUtils.applyOrder(wrapper, query);
        } else {
            wrapper.orderByAsc("dict_sort").orderByAsc("dict_code");
        }
        return PageResult.of(dictDataMapper.selectPage(PageUtils.toPage(query), wrapper), SysDictDataVO::of);
    }

    /**
     * 查询字典项详情。
     *
     * @param dictCode 字典编码
     * @return 字典项
     */
    public SysDictDataVO getDetail(Long dictCode) {
        BizException.throwIf(dictCode == null, "字典编码不能为空");
        SysDictData entity = dictDataMapper.selectById(dictCode);
        BizException.throwIf(entity == null, "字典数据不存在");
        return SysDictDataVO.of(entity);
    }

    /**
     * 新增字典项。
     * <p>校验：所属字典类型必须存在；同一字典类型下键值不允许重复。</p>
     *
     * @param dictData 字典项
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean add(SysDictData dictData) {
        checkRequired(dictData);
        checkTypeExists(dictData.getDictType());
        checkValueUnique(dictData.getDictType(), dictData.getDictValue(), null);

        dictData.setDictCode(null);
        if (dictData.getDictSort() == null) {
            dictData.setDictSort(0);
        }
        if (dictData.getIsDefault() == null) {
            dictData.setIsDefault(Constants.NO);
        }
        if (dictData.getStatus() == null) {
            dictData.setStatus(Constants.STATUS_NORMAL);
        }

        boolean success;
        try {
            success = dictDataMapper.insert(dictData) > 0;
        } catch (DuplicateKeyException e) {
            // 上面的 checkValueUnique 是「先查后插」，并发下可能同时通过检查，
            // 由 uk_dict_type_value 唯一索引兜底：把数据库异常翻译成友好提示
            throw new BizException("该字典类型下键值已存在：" + dictData.getDictValue());
        }
        if (success) {
            evictCache(dictData.getDictType());
        }
        return success;
    }

    /**
     * 修改字典项。
     * <p>若字典类型被改动，会同时失效新旧两个类型的缓存。</p>
     *
     * @param dictData 字典项，dictCode 必填
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean update(SysDictData dictData) {
        BizException.throwIf(dictData == null || dictData.getDictCode() == null, "字典编码不能为空");
        SysDictData old = dictDataMapper.selectById(dictData.getDictCode());
        BizException.throwIf(old == null, "字典数据不存在");
        checkRequired(dictData);

        String newType = dictData.getDictType();
        if (!newType.equals(old.getDictType())) {
            checkTypeExists(newType);
        }
        checkValueUnique(newType, dictData.getDictValue(), dictData.getDictCode());

        boolean success;
        try {
            success = dictDataMapper.updateById(dictData) > 0;
        } catch (DuplicateKeyException e) {
            // 并发下改成同类型下已存在的键值，由唯一索引兜底
            throw new BizException("该字典类型下键值已存在：" + dictData.getDictValue());
        }
        if (success) {
            evictCache(old.getDictType());
            evictCache(newType);
        }
        return success;
    }

    /**
     * 删除字典项。
     *
     * @param dictCode 字典编码
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long dictCode) {
        BizException.throwIf(dictCode == null, "字典编码不能为空");
        SysDictData old = dictDataMapper.selectById(dictCode);
        BizException.throwIf(old == null, "字典数据不存在");

        boolean success = dictDataMapper.deleteById(dictCode) > 0;
        if (success) {
            evictCache(old.getDictType());
        }
        return success;
    }

    /**
     * 失效指定字典类型的缓存。字典数据/类型发生变更后调用。
     *
     * @param dictType 字典类型，为空时忽略
     */
    public void evictCache(String dictType) {
        if (StringUtils.hasText(dictType)) {
            dictCache.remove(cacheKey(dictType));
            log.debug("字典缓存已失效：{}", cacheKey(dictType));
        }
    }

    /**
     * 清空全部字典缓存，用于字典表被外部批量变更后的兜底刷新。
     */
    public void clearCache() {
        dictCache.clear();
        log.info("字典缓存已全部清空");
    }

    /** 从库中装载某类型的全部字典项（只读列表） */
    private List<SysDictDataVO> loadFromDb(String dictType) {
        QueryWrapper<SysDictData> wrapper = new QueryWrapper<>();
        wrapper.eq("dict_type", dictType);
        wrapper.orderByAsc("dict_sort").orderByAsc("dict_code");
        List<SysDictDataVO> list = dictDataMapper.selectList(wrapper).stream()
                .map(SysDictDataVO::of)
                .toList();
        log.debug("字典装载入库缓存：{} 共 {} 项", cacheKey(dictType), list.size());
        return list;
    }

    /** 缓存 key：hparty:dict:{dictType} */
    private String cacheKey(String dictType) {
        return Constants.CACHE_DICT + dictType;
    }

    /** 必填校验 */
    private void checkRequired(SysDictData dictData) {
        BizException.throwIf(dictData == null, "字典数据不能为空");
        BizException.throwIf(!StringUtils.hasText(dictData.getDictType()), "字典类型不能为空");
        BizException.throwIf(!StringUtils.hasText(dictData.getDictLabel()), "字典标签不能为空");
        BizException.throwIf(!StringUtils.hasText(dictData.getDictValue()), "字典键值不能为空");
    }

    /** 字典类型必须已存在 */
    private void checkTypeExists(String dictType) {
        QueryWrapper<SysDictType> wrapper = new QueryWrapper<>();
        wrapper.eq("dict_type", dictType);
        BizException.throwIf(dictTypeMapper.selectCount(wrapper) <= 0, "字典类型不存在：" + dictType);
    }

    /** 同一字典类型下键值唯一 */
    private void checkValueUnique(String dictType, String dictValue, Long excludeDictCode) {
        QueryWrapper<SysDictData> wrapper = new QueryWrapper<>();
        wrapper.eq("dict_type", dictType);
        wrapper.eq("dict_value", dictValue);
        wrapper.ne(excludeDictCode != null, "dict_code", excludeDictCode);
        BizException.throwIf(dictDataMapper.selectCount(wrapper) > 0, "该字典类型下键值已存在：" + dictValue);
    }
}
