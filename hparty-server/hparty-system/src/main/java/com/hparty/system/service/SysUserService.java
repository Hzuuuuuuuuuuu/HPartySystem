package com.hparty.system.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hparty.common.constant.Constants;
import com.hparty.common.core.PageResult;
import com.hparty.common.exception.BizException;
import com.hparty.common.util.PasswordPolicy;
import com.hparty.framework.core.PageUtils;
import com.hparty.framework.datascope.DataScopeHelper;
import com.hparty.framework.security.SecurityUtils;
import com.hparty.system.domain.dto.SysUserDTO;
import com.hparty.system.domain.dto.SysUserQuery;
import com.hparty.system.domain.entity.SysDept;
import com.hparty.system.domain.entity.SysRole;
import com.hparty.system.domain.entity.SysUser;
import com.hparty.system.domain.vo.SysUserVO;
import com.hparty.system.mapper.SysDeptMapper;
import com.hparty.system.mapper.SysRelationMapper;
import com.hparty.system.mapper.SysRoleMapper;
import com.hparty.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 系统用户服务。
 *
 * <h3>数据权限</h3>
 * <p>列表查询通过 {@link DataScopeHelper#apply} 追加 SQL 条件，
 * 按主键的读/写操作通过 {@link DataScopeHelper#canAccessOrg} 做越权校验，
 * 两者缺一不可：前者防「列表里看到别人」，后者防「猜到 ID 直接调用接口」。</p>
 *
 * <h3>密码</h3>
 * <p>统一使用 Hutool 的 BCrypt（{@code $2a$} 前缀）加密后入库，
 * 明文密码不出 Service 层。新增与重置密码都会刷新 pwdUpdateDate。</p>
 */
@Service
@RequiredArgsConstructor
public class SysUserService {

    /** 密码最短长度 */
    /* 密码长度等强度要求已统一到 com.hparty.common.util.PasswordPolicy，此处不再单独定义 */

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysDeptMapper deptMapper;
    private final SysRelationMapper relationMapper;

    // ==================== 查询 ====================

    /**
     * 用户分页列表，已应用数据权限。
     *
     * @param query 过滤条件：username / nickName / phone 模糊，orgId / status 精确
     * @return 分页结果
     */
    public PageResult<SysUserVO> listUser(SysUserQuery query) {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(query.getUsername()), "username", query.getUsername());
        wrapper.like(StrUtil.isNotBlank(query.getNickName()), "nick_name", query.getNickName());
        wrapper.like(StrUtil.isNotBlank(query.getPhone()), "phone", query.getPhone());
        wrapper.eq(query.getOrgId() != null, "org_id", query.getOrgId());
        wrapper.eq(query.getStatus() != null, "status", query.getStatus());

        // 数据权限：sys_user 同时具备 org_id 与 person_id，使用默认字段
        DataScopeHelper.apply(wrapper);

        PageUtils.applyOrder(wrapper, query);
        if (StrUtil.isBlank(query.getOrderByColumn())) {
            wrapper.orderByDesc("user_id");
        }

        Page<SysUser> page = PageUtils.toPage(query);
        Page<SysUser> result = userMapper.selectPage(page, wrapper);

        Map<Long, String> orgNames = loadOrgNames(result.getRecords().stream()
                .map(SysUser::getOrgId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<Long, List<String>> roleNames = loadRoleNamesByUser(result.getRecords().stream()
                .map(SysUser::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return PageResult.of(result, user -> {
            SysUserVO vo = toVO(user, lookupOrgName(orgNames, user.getOrgId()));
            vo.setRoleNames(roleNames.getOrDefault(user.getUserId(), List.of()));
            return vo;
        });
    }

    /**
     * 用户详情，含已分配角色。
     *
     * @param userId 用户 ID
     * @return 用户视图对象
     */
    public SysUserVO getUser(Long userId) {
        SysUser user = requireUser(userId);
        checkDataAccess(user.getOrgId(), user.getPersonId());

        List<Long> roleIds = relationMapper.selectRoleIdsByUserId(userId);
        SysUserVO vo = toVO(user, loadOrgName(user.getOrgId()));
        vo.setRoleIds(roleIds);
        vo.setRoleNames(loadRoleNames(roleIds));
        return vo;
    }

    // ==================== 新增 / 修改 ====================

    /**
     * 新增用户并同步写入角色关联。
     *
     * @param dto 用户信息，password 必填
     * @return 新增的用户 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long addUser(SysUserDTO dto) {
        BizException.throwIf(StrUtil.isBlank(dto.getUsername()), "登录账号不能为空");
        BizException.throwIf(StrUtil.isBlank(dto.getPassword()), "初始密码不能为空");
        checkPassword(dto.getPassword(), dto.getUsername());
        BizException.throwIf(existsUsername(dto.getUsername(), null), "登录账号已存在");
        if (dto.getOrgId() != null) {
            checkDataAccess(dto.getOrgId(), dto.getPersonId());
        }

        SysUser user = new SysUser();
        user.setUsername(dto.getUsername().trim());
        user.setPassword(BCrypt.hashpw(dto.getPassword()));
        user.setNickName(dto.getNickName());
        user.setPersonId(dto.getPersonId());
        user.setOrgId(dto.getOrgId());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setAvatar(dto.getAvatar());
        user.setSex(dto.getSex() == null ? 0 : dto.getSex());
        user.setStatus(dto.getStatus() == null ? Constants.STATUS_NORMAL : dto.getStatus());
        user.setRemark(dto.getRemark());
        user.setPwdUpdateDate(LocalDateTime.now());
        userMapper.insert(user);

        saveUserRoles(user.getUserId(), dto.getRoleIds());
        return user.getUserId();
    }

    /**
     * 修改用户并全量覆盖角色关联。
     * <p>密码不在此处修改，请使用 {@link #resetPwd}。</p>
     *
     * @param dto 用户信息，userId 必填
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(SysUserDTO dto) {
        BizException.throwIf(dto.getUserId() == null, "用户ID不能为空");
        SysUser old = requireUser(dto.getUserId());
        checkDataAccess(old.getOrgId(), old.getPersonId());

        if (StrUtil.isNotBlank(dto.getUsername())) {
            BizException.throwIf(existsUsername(dto.getUsername(), dto.getUserId()), "登录账号已存在");
        }
        if (dto.getOrgId() != null) {
            checkDataAccess(dto.getOrgId(), dto.getPersonId());
        }
        if (Objects.equals(dto.getUserId(), SecurityUtils.getUserId())
                && dto.getStatus() != null && dto.getStatus() == Constants.STATUS_DISABLED) {
            throw new BizException("不允许停用当前登录账号");
        }

        SysUser user = new SysUser();
        user.setUserId(dto.getUserId());
        user.setUsername(StrUtil.isBlank(dto.getUsername()) ? null : dto.getUsername().trim());
        user.setNickName(dto.getNickName());
        user.setPersonId(dto.getPersonId());
        user.setOrgId(dto.getOrgId());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setAvatar(dto.getAvatar());
        user.setSex(dto.getSex());
        user.setStatus(dto.getStatus());
        user.setRemark(dto.getRemark());
        userMapper.updateById(user);

        if (dto.getRoleIds() != null) {
            saveUserRoles(dto.getUserId(), dto.getRoleIds());
        }
    }

    /**
     * 删除用户（逻辑删除）并清理角色关联。
     *
     * @param userId 用户 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeUser(Long userId) {
        SysUser user = requireUser(userId);
        BizException.throwIf(Constants.SUPER_ADMIN_USER_ID.equals(userId), "超级管理员账号不允许删除");
        BizException.throwIf(userId.equals(SecurityUtils.getUserId()), "不允许删除当前登录账号");
        checkDataAccess(user.getOrgId(), user.getPersonId());

        userMapper.deleteById(userId);
        relationMapper.deleteUserRolesByUserId(userId);
    }

    /**
     * 重置密码。
     *
     * @param userId   用户 ID
     * @param password 新的明文密码
     */
    public void resetPwd(Long userId, String password) {
        BizException.throwIf(StrUtil.isBlank(password), "密码不能为空");

        SysUser user = requireUser(userId);
        checkDataAccess(user.getOrgId(), user.getPersonId());
        checkPassword(password, user.getUsername());

        SysUser patch = new SysUser();
        patch.setUserId(userId);
        patch.setPassword(BCrypt.hashpw(password));
        patch.setPwdUpdateDate(LocalDateTime.now());
        userMapper.updateById(patch);
    }

    /**
     * 启用 / 停用用户。
     *
     * @param userId 用户 ID
     * @param status 0=停用 1=正常
     */
    public void changeStatus(Long userId, Integer status) {
        BizException.throwIf(status == null, "状态不能为空");
        SysUser user = requireUser(userId);
        BizException.throwIf(Constants.SUPER_ADMIN_USER_ID.equals(userId), "超级管理员账号不允许停用");
        if (status == Constants.STATUS_DISABLED) {
            BizException.throwIf(userId.equals(SecurityUtils.getUserId()), "不允许停用当前登录账号");
        }
        checkDataAccess(user.getOrgId(), user.getPersonId());

        SysUser patch = new SysUser();
        patch.setUserId(userId);
        patch.setStatus(status);
        userMapper.updateById(patch);
    }

    // ==================== 内部方法 ====================

    /** 全量覆盖用户的角色关联 */
    private void saveUserRoles(Long userId, List<Long> roleIds) {
        relationMapper.deleteUserRolesByUserId(userId);
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        List<Long> distinct = roleIds.stream().filter(Objects::nonNull).distinct().toList();
        if (!distinct.isEmpty()) {
            relationMapper.insertUserRoles(userId, distinct);
        }
    }

    /** 登录账号唯一性校验，excludeUserId 用于修改场景排除自身 */
    private boolean existsUsername(String username, Long excludeUserId) {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<SysUser>().eq("username", username.trim());
        if (excludeUserId != null) {
            wrapper.ne("user_id", excludeUserId);
        }
        return userMapper.selectCount(wrapper) > 0;
    }

    /**
     * 校验密码强度。
     *
     * <p>委托给 {@link com.hparty.common.util.PasswordPolicy} —— 新增用户、重置密码、
     * 用户自助改密三处共用同一套规则与文案，避免出现「这边能设那边不能设」的不一致。</p>
     *
     * @param password 明文密码
     * @param username 登录账号，用于拦截「密码包含账号」；可为 null
     */
    private void checkPassword(String password, String username) {
        String reason = PasswordPolicy.validate(password, username);
        BizException.throwIf(reason != null, reason);
    }

    private SysUser requireUser(Long userId) {
        BizException.throwIf(userId == null, "用户ID不能为空");
        SysUser user = userMapper.selectById(userId);
        BizException.throwIf(user == null, "用户不存在");
        return user;
    }

    private void checkDataAccess(Long orgId, Long personId) {
        BizException.throwForbiddenIf(!DataScopeHelper.canAccessData(orgId, personId), "无权操作其他党组织或其他人员的用户");
    }

    private String loadOrgName(Long orgId) {
        if (orgId == null) {
            return null;
        }
        SysDept dept = deptMapper.selectById(orgId);
        return dept == null ? null : dept.getOrgName();
    }

    /** 批量取组织名称，避免逐行查库 */
    private Map<Long, String> loadOrgNames(Set<Long> orgIds) {
        if (orgIds.isEmpty()) {
            return Map.of();
        }
        List<SysDept> depts = deptMapper.selectList(new QueryWrapper<SysDept>()
                .select("org_id", "org_name")
                .in("org_id", orgIds));
        return depts.stream().collect(Collectors.toMap(
                SysDept::getOrgId, SysDept::getOrgName, (a, b) -> a));
    }

    /**
     * 取组织名称，orgId 为 null 时直接返回 null。
     *
     * <p>必须显式判空：{@link #loadOrgNames} 在无组织可查时返回的是 {@code Map.of()}，
     * 而不可变空 Map 的 {@code get(null)} 会抛 NullPointerException
     * （{@code Collections.emptyMap().get(null)} 才是返回 null）。
     * 超级管理员这类 {@code org_id} 为 NULL 的账号没有归属组织，
     * 列表里只出现这类账号时就会命中该路径 —— 表现为「搜 admin 报空指针」。</p>
     */
    private String lookupOrgName(Map<Long, String> orgNames, Long orgId) {
        return orgId == null ? null : orgNames.get(orgId);
    }

    /** 批量取「用户 → 角色名称」，避免逐行查库；无角色的用户不会出现在返回的 Map 中 */
    private Map<Long, List<String>> loadRoleNamesByUser(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> result = new HashMap<>();
        for (Map<String, Object> row : relationMapper.selectRoleNamesByUserIds(userIds)) {
            Object userId = row.get("user_id");
            Object roleName = row.get("role_name");
            if (!(userId instanceof Number id) || roleName == null) {
                continue;
            }
            result.computeIfAbsent(id.longValue(), k -> new ArrayList<>()).add(String.valueOf(roleName));
        }
        return result;
    }

    private List<String> loadRoleNames(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        List<SysRole> roles = roleMapper.selectList(new QueryWrapper<SysRole>()
                .select("role_id", "role_name")
                .in("role_id", roleIds));
        return roles.stream().map(SysRole::getRoleName).filter(StrUtil::isNotBlank).toList();
    }

    private SysUserVO toVO(SysUser user, String orgName) {
        SysUserVO vo = new SysUserVO();
        vo.setUserId(user.getUserId());
        vo.setUsername(user.getUsername());
        vo.setNickName(user.getNickName());
        vo.setPersonId(user.getPersonId());
        vo.setOrgId(user.getOrgId());
        vo.setOrgName(orgName);
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setSex(user.getSex());
        vo.setStatus(user.getStatus());
        vo.setLoginIp(user.getLoginIp());
        vo.setLoginDate(user.getLoginDate());
        vo.setRemark(user.getRemark());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }
}
