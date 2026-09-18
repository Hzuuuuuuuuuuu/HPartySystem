package com.hparty.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hparty.common.constant.Constants;
import com.hparty.common.core.PageResult;
import com.hparty.common.exception.BizException;
import com.hparty.framework.core.PageUtils;
import com.hparty.system.domain.dto.SysRoleDTO;
import com.hparty.system.domain.dto.SysRoleQuery;
import com.hparty.system.domain.entity.SysRole;
import com.hparty.system.domain.vo.SysRoleVO;
import com.hparty.system.mapper.SysRelationMapper;
import com.hparty.system.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 角色服务。
 *
 * <h3>删除保护</h3>
 * <ul>
 *   <li>{@code is_builtin = 1} 的内置角色（超级管理员、支部书记等）不允许删除</li>
 *   <li>已被用户引用的角色不允许删除，否则用户会丢失全部权限</li>
 * </ul>
 *
 * <h3>数据范围</h3>
 * <p>{@code data_scope = 5}（自定义）时，角色的可见组织范围由 {@code sys_role_dept}
 * 决定，登录时由认证逻辑汇总写入会话，Service 层只需保证关联表被正确维护。</p>
 */
@Service
@RequiredArgsConstructor
public class SysRoleService {

    /** 数据范围：自定义 */
    private static final int DATA_SCOPE_CUSTOM = 5;

    private final SysRoleMapper roleMapper;
    private final SysRelationMapper relationMapper;

    // ==================== 查询 ====================

    /**
     * 角色分页列表。
     *
     * @param query 过滤条件：roleName / roleKey 模糊，status 精确
     * @return 分页结果
     */
    public PageResult<SysRoleVO> listRole(SysRoleQuery query) {
        QueryWrapper<SysRole> wrapper = new QueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(query.getRoleName()), "role_name", query.getRoleName());
        wrapper.like(StrUtil.isNotBlank(query.getRoleKey()), "role_key", query.getRoleKey());
        wrapper.eq(query.getStatus() != null, "status", query.getStatus());

        PageUtils.applyOrder(wrapper, query);
        if (StrUtil.isBlank(query.getOrderByColumn())) {
            wrapper.orderByAsc("role_sort");
            wrapper.orderByAsc("role_id");
        }

        Page<SysRole> page = PageUtils.toPage(query);
        Page<SysRole> result = roleMapper.selectPage(page, wrapper);
        return PageResult.of(result, this::toVO);
    }

    /**
     * 角色详情，含已分配菜单 ID 与自定义组织范围。
     *
     * @param roleId 角色 ID
     * @return 角色视图对象
     */
    public SysRoleVO getRole(Long roleId) {
        SysRole role = requireRole(roleId);
        SysRoleVO vo = toVO(role);
        vo.setMenuIds(relationMapper.selectMenuIdsByRoleId(roleId));
        vo.setDeptIds(relationMapper.selectOrgIdsByRoleId(roleId));
        return vo;
    }

    // ==================== 新增 / 修改 ====================

    /**
     * 新增角色并同步菜单 / 组织范围关联。
     *
     * @param dto 角色信息
     * @return 新增的角色 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long addRole(SysRoleDTO dto) {
        validate(dto);
        BizException.throwIf(existsRoleKey(dto.getRoleKey(), null), "角色权限字符串已存在");

        SysRole role = new SysRole();
        role.setRoleName(dto.getRoleName().trim());
        role.setRoleKey(dto.getRoleKey().trim());
        role.setRoleSort(dto.getRoleSort() == null ? 0 : dto.getRoleSort());
        role.setDataScope(dto.getDataScope() == null ? 3 : dto.getDataScope());
        role.setStatus(dto.getStatus() == null ? Constants.STATUS_NORMAL : dto.getStatus());
        role.setRemark(dto.getRemark());
        role.setIsBuiltin(Constants.NO);
        roleMapper.insert(role);

        saveMenus(role.getRoleId(), dto.getMenuIds());
        saveDepts(role.getRoleId(), role.getDataScope(), dto.getDeptIds());
        return role.getRoleId();
    }

    /**
     * 修改角色并全量覆盖菜单 / 组织范围关联。
     *
     * @param dto 角色信息，roleId 必填
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(SysRoleDTO dto) {
        BizException.throwIf(dto.getRoleId() == null, "角色ID不能为空");
        SysRole old = requireRole(dto.getRoleId());
        validate(dto);
        if (StrUtil.isNotBlank(dto.getRoleKey())) {
            BizException.throwIf(existsRoleKey(dto.getRoleKey(), dto.getRoleId()), "角色权限字符串已存在");
        }

        SysRole role = new SysRole();
        role.setRoleId(dto.getRoleId());
        role.setRoleName(dto.getRoleName() == null ? null : dto.getRoleName().trim());
        role.setRoleKey(StrUtil.isBlank(dto.getRoleKey()) ? null : dto.getRoleKey().trim());
        role.setRoleSort(dto.getRoleSort());
        role.setDataScope(dto.getDataScope());
        role.setStatus(dto.getStatus());
        role.setRemark(dto.getRemark());
        roleMapper.updateById(role);

        if (dto.getMenuIds() != null) {
            saveMenus(dto.getRoleId(), dto.getMenuIds());
        }
        Integer dataScope = dto.getDataScope() != null ? dto.getDataScope() : old.getDataScope();
        if (dto.getDeptIds() != null || !Objects.equals(dataScope, DATA_SCOPE_CUSTOM)) {
            saveDepts(dto.getRoleId(), dataScope, dto.getDeptIds());
        }
    }

    /**
     * 删除角色。
     *
     * @param roleId 角色 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeRole(Long roleId) {
        SysRole role = requireRole(roleId);
        BizException.throwIf(Objects.equals(role.getIsBuiltin(), Constants.YES), "内置角色不允许删除");

        long userCount = relationMapper.countUsersByRoleId(roleId);
        BizException.throwIf(userCount > 0, "该角色已分配给 " + userCount + " 个用户，不允许删除");

        roleMapper.deleteById(roleId);
        relationMapper.deleteRoleMenusByRoleId(roleId);
        relationMapper.deleteRoleDeptsByRoleId(roleId);
        relationMapper.deleteUserRolesByRoleId(roleId);
    }

    // ==================== 授权 ====================

    /**
     * 分配菜单权限，全量覆盖。
     *
     * @param roleId  角色 ID
     * @param menuIds 菜单 ID 集合，为空表示清空全部权限
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds) {
        requireRole(roleId);
        saveMenus(roleId, menuIds);
    }

    /**
     * 启用 / 停用角色。
     *
     * @param roleId 角色 ID
     * @param status 0=停用 1=正常
     */
    public void changeStatus(Long roleId, Integer status) {
        BizException.throwIf(status == null, "状态不能为空");
        SysRole role = requireRole(roleId);
        BizException.throwIf(Constants.SUPER_ADMIN_ROLE.equals(role.getRoleKey()), "超级管理员角色不允许停用");

        SysRole patch = new SysRole();
        patch.setRoleId(roleId);
        patch.setStatus(status);
        roleMapper.updateById(patch);
    }

    // ==================== 内部方法 ====================

    /** 全量覆盖角色菜单关联 */
    private void saveMenus(Long roleId, List<Long> menuIds) {
        relationMapper.deleteRoleMenusByRoleId(roleId);
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        List<Long> distinct = menuIds.stream().filter(Objects::nonNull).distinct().toList();
        if (!distinct.isEmpty()) {
            relationMapper.insertRoleMenus(roleId, distinct);
        }
    }

    /** 维护自定义数据范围的组织关联；非自定义范围一律清空 */
    private void saveDepts(Long roleId, Integer dataScope, List<Long> deptIds) {
        relationMapper.deleteRoleDeptsByRoleId(roleId);
        if (dataScope == null || dataScope != DATA_SCOPE_CUSTOM || deptIds == null || deptIds.isEmpty()) {
            return;
        }
        List<Long> distinct = deptIds.stream().filter(Objects::nonNull).distinct().toList();
        if (!distinct.isEmpty()) {
            relationMapper.insertRoleDepts(roleId, distinct);
        }
    }

    /** 角色权限字符串唯一性校验，excludeRoleId 用于修改场景排除自身 */
    private boolean existsRoleKey(String roleKey, Long excludeRoleId) {
        if (StrUtil.isBlank(roleKey)) {
            return false;
        }
        QueryWrapper<SysRole> wrapper = new QueryWrapper<SysRole>().eq("role_key", roleKey.trim());
        if (excludeRoleId != null) {
            wrapper.ne("role_id", excludeRoleId);
        }
        return roleMapper.selectCount(wrapper) > 0;
    }

    private SysRole requireRole(Long roleId) {
        BizException.throwIf(roleId == null, "角色ID不能为空");
        SysRole role = roleMapper.selectById(roleId);
        BizException.throwIf(role == null, "角色不存在");
        return role;
    }

    private void validate(SysRoleDTO dto) {
        BizException.throwIf(StrUtil.isBlank(dto.getRoleName()), "角色名称不能为空");
        BizException.throwIf(StrUtil.isBlank(dto.getRoleKey()), "角色权限字符串不能为空");
        if (dto.getDataScope() != null) {
            BizException.throwIf(dto.getDataScope() < 1 || dto.getDataScope() > 5,
                    "数据范围不合法：1=全部 2=本级 3=本级及以下 4=仅本人 5=自定义");
        }
    }

    private SysRoleVO toVO(SysRole role) {
        SysRoleVO vo = new SysRoleVO();
        vo.setRoleId(role.getRoleId());
        vo.setRoleName(role.getRoleName());
        vo.setRoleKey(role.getRoleKey());
        vo.setRoleSort(role.getRoleSort());
        vo.setDataScope(role.getDataScope());
        vo.setIsBuiltin(role.getIsBuiltin());
        vo.setStatus(role.getStatus());
        vo.setRemark(role.getRemark());
        vo.setCreateTime(role.getCreateTime());
        return vo;
    }
}
