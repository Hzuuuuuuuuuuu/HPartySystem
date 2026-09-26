package com.hparty.party.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hparty.common.core.PageResult;
import com.hparty.common.exception.BizException;
import com.hparty.framework.core.PageUtils;
import com.hparty.framework.datascope.DataScopeHelper;
import com.hparty.framework.security.SecurityUtils;
import com.hparty.party.domain.dto.MemberServiceQuery;
import com.hparty.party.domain.entity.MemberService;
import com.hparty.party.enums.ServiceStatusEnum;
import com.hparty.party.enums.ServiceTypeEnum;
import com.hparty.party.mapper.MemberServiceMapper;
import com.hparty.party.mapper.PartyLookupMapper;
import com.hparty.party.util.PartyNameUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 党员服务管理服务（困难帮扶、走访慰问、志愿服务等）。
 */
@Service
@RequiredArgsConstructor
public class MemberServiceService {

    private final MemberServiceMapper serviceMapper;
    private final PartyLookupMapper lookupMapper;

    /** 分页查询党员服务记录。 */
    public PageResult<Map<String, Object>> page(MemberServiceQuery query) {
        LambdaQueryWrapper<MemberService> wrapper = new LambdaQueryWrapper<>();
        DataScopeHelper.apply(wrapper);
        if (query.getOrgId() != null) {
            wrapper.eq(MemberService::getOrgId, query.getOrgId());
        }
        if (query.getServiceType() != null) {
            wrapper.eq(MemberService::getServiceType, query.getServiceType());
        }
        if (query.getStatus() != null) {
            wrapper.eq(MemberService::getStatus, query.getStatus());
        }
        if (StrUtil.isNotBlank(query.getPersonName())) {
            wrapper.like(MemberService::getPersonName, query.getPersonName().trim());
        }
        if (StrUtil.isNotBlank(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w.like(MemberService::getTitle, kw)
                    .or().like(MemberService::getContent, kw)
                    .or().like(MemberService::getHandlerName, kw));
        }
        wrapper.orderByDesc(MemberService::getServiceDate).orderByDesc(MemberService::getServiceId);

        Page<MemberService> page = serviceMapper.selectPage(PageUtils.toPage(query), wrapper);
        Map<Long, String> orgNames = loadOrgNames(page.getRecords().stream()
                .map(MemberService::getOrgId).toList());
        return PageResult.of(page, s -> toVO(s, orgNames));
    }

    /** 服务详情。 */
    public Map<String, Object> detail(Long serviceId) {
        MemberService service = get(serviceId);
        return toVO(service, loadOrgNames(Collections.singletonList(service.getOrgId())));
    }

    /** 按主键取服务记录并做越权校验。 */
    public MemberService get(Long serviceId) {
        if (serviceId == null) {
            throw new BizException("服务ID不能为空");
        }
        MemberService service = serviceMapper.selectById(serviceId);
        if (service == null) {
            throw new BizException("党员服务记录不存在");
        }
        if (!DataScopeHelper.canAccessData(service.getOrgId(), service.getPersonId())) {
            throw BizException.forbidden("无权查看其他党组织或其他人员的党员服务记录");
        }
        return service;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long add(MemberService service) {
        if (StrUtil.isBlank(service.getTitle())) {
            throw new BizException("请填写服务事项");
        }
        service.setServiceId(null);
        if (service.getOrgId() == null) {
            service.setOrgId(SecurityUtils.getOrgId());
        }
        // 无归属组织的账号提前拦下：member_service.org_id 是 NOT NULL（MySQL 1364）。
        BizException.throwIf(service.getOrgId() == null, "当前账号未分配所属党组织，无法创建。");
        if (service.getServiceType() == null) {
            service.setServiceType(ServiceTypeEnum.DIFFICULTY_HELP.getCode());
        }
        if (service.getStatus() == null) {
            service.setStatus(ServiceStatusEnum.PENDING.getCode());
        }
        fillNames(service);
        serviceMapper.insert(service);
        return service.getServiceId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(MemberService service) {
        if (service.getServiceId() == null) {
            throw new BizException("服务ID不能为空");
        }
        MemberService exists = get(service.getServiceId());
        service.setOrgId(exists.getOrgId());
        fillNames(service);
        serviceMapper.updateById(service);
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(Long serviceId) {
        get(serviceId);
        serviceMapper.deleteById(serviceId);
    }

    /**
     * 党员服务统计：总数、按类型分布、按状态分布、帮扶金额合计。
     */
    public Map<String, Object> statistics(Long orgId) {
        LambdaQueryWrapper<MemberService> wrapper = new LambdaQueryWrapper<>();
        DataScopeHelper.apply(wrapper);
        if (orgId != null) {
            wrapper.eq(MemberService::getOrgId, orgId);
        }
        List<MemberService> list = serviceMapper.selectList(wrapper);

        Map<Integer, Long> typeGrouped = new LinkedHashMap<>();
        Map<Integer, Long> statusGrouped = new LinkedHashMap<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (MemberService s : list) {
            if (s.getServiceType() != null) {
                typeGrouped.merge(s.getServiceType(), 1L, Long::sum);
            }
            if (s.getStatus() != null) {
                statusGrouped.merge(s.getStatus(), 1L, Long::sum);
            }
            if (s.getAmount() != null) {
                totalAmount = totalAmount.add(s.getAmount());
            }
        }

        List<Map<String, Object>> byType = new ArrayList<>();
        for (ServiceTypeEnum type : ServiceTypeEnum.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", type.getCode());
            item.put("typeLabel", type.getLabel());
            item.put("count", typeGrouped.getOrDefault(type.getCode(), 0L));
            byType.add(item);
        }

        List<Map<String, Object>> byStatus = new ArrayList<>();
        for (ServiceStatusEnum status : ServiceStatusEnum.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("status", status.getCode());
            item.put("statusLabel", status.getLabel());
            item.put("count", statusGrouped.getOrDefault(status.getCode(), 0L));
            byStatus.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", (long) list.size());
        result.put("byType", byType);
        result.put("byStatus", byStatus);
        result.put("totalAmount", totalAmount);
        return result;
    }

    /** 服务对象、经办人姓名未填时按人员档案补齐 */
    private void fillNames(MemberService service) {
        if (StrUtil.isBlank(service.getPersonName()) && service.getPersonId() != null) {
            service.setPersonName(lookupMapper.selectPersonName(service.getPersonId()));
        }
        if (StrUtil.isBlank(service.getHandlerName()) && service.getHandlerId() != null) {
            service.setHandlerName(lookupMapper.selectPersonName(service.getHandlerId()));
        }
    }

    /** 批量补组织名，避免 N+1 */
    private Map<Long, String> loadOrgNames(List<Long> orgIds) {
        List<Long> ids = orgIds.stream().filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of()
                : PartyNameUtils.toOrgNameMap(lookupMapper.selectOrgNames(ids));
    }

    private Map<String, Object> toVO(MemberService s, Map<Long, String> orgNames) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("serviceId", s.getServiceId());
        item.put("title", s.getTitle());
        item.put("serviceType", s.getServiceType());
        item.put("serviceTypeLabel", ServiceTypeEnum.labelOf(s.getServiceType()));
        item.put("personId", s.getPersonId());
        item.put("personName", s.getPersonName());
        item.put("orgId", s.getOrgId());
        item.put("orgName", orgNames.get(s.getOrgId()));
        item.put("serviceDate", s.getServiceDate());
        item.put("content", s.getContent());
        item.put("amount", s.getAmount());
        item.put("handlerId", s.getHandlerId());
        item.put("handlerName", s.getHandlerName());
        item.put("status", s.getStatus());
        item.put("statusLabel", ServiceStatusEnum.labelOf(s.getStatus()));
        item.put("result", s.getResult());
        item.put("fileId", s.getFileId());
        item.put("fileUrl", s.getFileUrl());
        item.put("remark", s.getRemark());
        return item;
    }
}
