package com.hparty.party.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hparty.common.enums.MaterialCategory;
import com.hparty.common.exception.BizException;
import com.hparty.framework.datascope.DataScopeHelper;
import com.hparty.framework.security.LoginUser;
import com.hparty.framework.security.SecurityUtils;
import com.hparty.party.domain.entity.AmMaterial;
import com.hparty.party.mapper.AmMaterialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 组织生活会材料服务。
 *
 * <p>对应图2 的 11 个材料入口：通知、会前学习、记录、党员剖析材料、党员自评材料、
 * 其它内容、问题清单、整改清单、会议记录、民主评议党员、情况报告。
 * 11 类共用一张 {@code am_material} 表，用 {@code category} 区分。</p>
 */
@Service
@RequiredArgsConstructor
public class AmMaterialService {

    private final AmMaterialMapper materialMapper;

    /**
     * 按分类查询材料。
     */
    public List<Map<String, Object>> list(String category, Long meetingId) {
        var wrapper = new LambdaQueryWrapper<AmMaterial>().orderByDesc(AmMaterial::getCreateTime);
        // 本表无 person_id 列，personColumn 传 null（详见 AmMeetingService 的说明）
        DataScopeHelper.apply(wrapper, "org_id", null);
        if (StrUtil.isNotBlank(category)) {
            wrapper.eq(AmMaterial::getCategory, category);
        }
        if (meetingId != null) {
            wrapper.eq(AmMaterial::getMeetingId, meetingId);
        }

        return materialMapper.selectList(wrapper).stream().map(m -> {
            Map<String, Object> item = new HashMap<>();
            item.put("materialId", m.getMaterialId());
            item.put("meetingId", m.getMeetingId());
            item.put("orgId", m.getOrgId());
            item.put("category", m.getCategory());
            item.put("categoryLabel", MaterialCategory.labelOf(m.getCategory()));
            item.put("title", m.getTitle());
            item.put("content", m.getContent());
            item.put("fileId", m.getFileId());
            item.put("fileUrl", m.getFileUrl());
            item.put("uploadName", m.getUploadName());
            item.put("createTime", m.getCreateTime());
            return item;
        }).toList();
    }

    public AmMaterial get(Long materialId) {
        AmMaterial material = materialMapper.selectById(materialId);
        if (material == null) {
            throw new BizException("材料不存在");
        }
        if (!DataScopeHelper.canAccessOrg(material.getOrgId())) {
            throw BizException.forbidden("无权查看其他党组织的材料");
        }
        return material;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long add(AmMaterial material) {
        if (StrUtil.isBlank(material.getCategory())) {
            throw new BizException("请选择材料分类");
        }
        if (StrUtil.isBlank(material.getTitle())) {
            throw new BizException("请填写材料标题");
        }
        material.setMaterialId(null);

        LoginUser user = SecurityUtils.getLoginUser();
        if (material.getOrgId() == null) {
            material.setOrgId(user.getOrgId());
        }
        // 无归属组织的账号提前拦下：am_material.org_id 是 NOT NULL（MySQL 1364）。
        BizException.throwIf(material.getOrgId() == null, "当前账号未分配所属党组织，无法创建。");
        material.setUploadBy(user.getPersonId());
        material.setUploadName(user.getNickName() == null ? user.getUsername() : user.getNickName());
        materialMapper.insert(material);
        return material.getMaterialId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(AmMaterial material) {
        if (material.getMaterialId() == null) {
            throw new BizException("材料ID不能为空");
        }
        get(material.getMaterialId());
        materialMapper.updateById(material);
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(Long materialId) {
        get(materialId);
        materialMapper.deleteById(materialId);
    }
}
