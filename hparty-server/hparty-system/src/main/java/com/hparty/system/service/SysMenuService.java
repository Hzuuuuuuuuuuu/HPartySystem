package com.hparty.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hparty.common.constant.Constants;
import com.hparty.common.core.PageResult;
import com.hparty.common.exception.BizException;
import com.hparty.framework.core.PageUtils;
import com.hparty.system.domain.dto.SysMenuDTO;
import com.hparty.system.domain.dto.SysMenuQuery;
import com.hparty.system.domain.entity.SysMenu;
import com.hparty.system.domain.vo.SysMenuTreeVO;
import com.hparty.system.mapper.SysMenuMapper;
import com.hparty.system.mapper.SysRelationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单服务。
 *
 * <h3>菜单类型</h3>
 * <p>{@code M}=目录、{@code C}=菜单、{@code F}=按钮。只有 F 类型必须填权限标识
 * （如 {@code system:user:list}），登录时被汇总进会话供 {@code @SaCheckPermission} 校验。</p>
 *
 * <p>sys_menu 表没有逻辑删除列，删除是物理删除；同时会清理 {@code sys_role_menu}
 * 中的授权关系，避免角色残留无效菜单 ID。</p>
 */
@Service
@RequiredArgsConstructor
public class SysMenuService {

    /** 顶级菜单的父 ID */
    private static final Long ROOT_MENU_ID = 0L;

    /** 合法的菜单类型 */
    private static final Set<String> MENU_TYPES = Set.of("M", "C", "F");

    private final SysMenuMapper menuMapper;
    private final SysRelationMapper relationMapper;

    // ==================== 查询 ====================

    /**
     * 菜单分页列表。
     *
     * @param query 过滤条件：menuName / perms 模糊，parentId / menuType / status 精确
     * @return 分页结果
     */
    public PageResult<SysMenu> listMenus(SysMenuQuery query) {
        QueryWrapper<SysMenu> wrapper = new QueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(query.getMenuName()), "menu_name", query.getMenuName());
        wrapper.like(StrUtil.isNotBlank(query.getPerms()), "perms", query.getPerms());
        wrapper.eq(query.getParentId() != null, "parent_id", query.getParentId());
        wrapper.eq(StrUtil.isNotBlank(query.getMenuType()), "menu_type", query.getMenuType());
        wrapper.eq(query.getStatus() != null, "status", query.getStatus());

        PageUtils.applyOrder(wrapper, query);
        if (StrUtil.isBlank(query.getOrderByColumn())) {
            wrapper.orderByAsc("parent_id");
            wrapper.orderByAsc("order_num");
        }

        Page<SysMenu> page = PageUtils.toPage(query);
        return PageResult.of(menuMapper.selectPage(page, wrapper));
    }

    /**
     * 菜单树，供角色分配权限时勾选。
     *
     * @return 根节点集合
     */
    public List<SysMenuTreeVO> listMenuTree() {
        List<SysMenu> menus = menuMapper.selectList(new QueryWrapper<SysMenu>()
                .orderByAsc("order_num")
                .orderByAsc("menu_id"));
        return buildTree(menus);
    }

    /**
     * 菜单详情。
     *
     * @param menuId 菜单 ID
     * @return 菜单实体
     */
    public SysMenu getMenu(Long menuId) {
        return requireMenu(menuId);
    }

    // ==================== 新增 / 修改 ====================

    /**
     * 新增菜单。
     *
     * @param dto 菜单信息
     * @return 新增的菜单 ID
     */
    public Long addMenu(SysMenuDTO dto) {
        validate(dto);
        Long parentId = dto.getParentId() == null ? ROOT_MENU_ID : dto.getParentId();
        if (!Objects.equals(parentId, ROOT_MENU_ID)) {
            BizException.throwIf(menuMapper.selectById(parentId) == null, "上级菜单不存在");
        }

        SysMenu menu = new SysMenu();
        menu.setParentId(parentId);
        menu.setMenuName(dto.getMenuName().trim());
        menu.setOrderNum(dto.getOrderNum() == null ? 0 : dto.getOrderNum());
        menu.setPath(dto.getPath());
        menu.setComponent(dto.getComponent());
        menu.setQuery(dto.getQuery());
        menu.setIsFrame(dto.getIsFrame() == null ? Constants.NO : dto.getIsFrame());
        menu.setIsCache(dto.getIsCache() == null ? Constants.YES : dto.getIsCache());
        menu.setMenuType(dto.getMenuType());
        menu.setVisible(dto.getVisible() == null ? Constants.YES : dto.getVisible());
        menu.setStatus(dto.getStatus() == null ? Constants.STATUS_NORMAL : dto.getStatus());
        menu.setPerms(dto.getPerms());
        menu.setIcon(dto.getIcon());
        menu.setRemark(dto.getRemark());
        menuMapper.insert(menu);
        return menu.getMenuId();
    }

    /**
     * 修改菜单。不允许把菜单挂到自己或自己的子孙下面，否则菜单树会成环。
     *
     * @param dto 菜单信息，menuId 必填
     */
    public void updateMenu(SysMenuDTO dto) {
        BizException.throwIf(dto.getMenuId() == null, "菜单ID不能为空");
        requireMenu(dto.getMenuId());
        validate(dto);

        Long parentId = dto.getParentId();
        if (parentId != null && !Objects.equals(parentId, ROOT_MENU_ID)) {
            BizException.throwIf(Objects.equals(parentId, dto.getMenuId()), "上级菜单不能是自己");
            BizException.throwIf(menuMapper.selectById(parentId) == null, "上级菜单不存在");
            Set<Long> descendants = collectDescendantIds(dto.getMenuId());
            BizException.throwIf(descendants.contains(parentId), "上级菜单不能是自己的下级");
        }

        SysMenu menu = new SysMenu();
        menu.setMenuId(dto.getMenuId());
        menu.setParentId(parentId);
        menu.setMenuName(dto.getMenuName() == null ? null : dto.getMenuName().trim());
        menu.setOrderNum(dto.getOrderNum());
        menu.setPath(dto.getPath());
        menu.setComponent(dto.getComponent());
        menu.setQuery(dto.getQuery());
        menu.setIsFrame(dto.getIsFrame());
        menu.setIsCache(dto.getIsCache());
        menu.setMenuType(dto.getMenuType());
        menu.setVisible(dto.getVisible());
        menu.setStatus(dto.getStatus());
        menu.setPerms(dto.getPerms());
        menu.setIcon(dto.getIcon());
        menu.setRemark(dto.getRemark());
        menuMapper.updateById(menu);
    }

    /**
     * 删除菜单。存在子菜单时拒绝删除，同时清理角色授权关系。
     *
     * @param menuId 菜单 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeMenu(Long menuId) {
        requireMenu(menuId);

        long childCount = menuMapper.selectCount(new QueryWrapper<SysMenu>().eq("parent_id", menuId));
        BizException.throwIf(childCount > 0, "存在子菜单，不允许删除");

        relationMapper.deleteRoleMenusByMenuId(menuId);
        menuMapper.deleteById(menuId);
    }

    // ==================== 内部方法 ====================

    /** 广度优先收集全部子孙菜单 ID（不含自身） */
    private Set<Long> collectDescendantIds(Long menuId) {
        List<SysMenu> all = menuMapper.selectList(new QueryWrapper<SysMenu>().select("menu_id", "parent_id"));
        Map<Long, List<Long>> childrenOf = all.stream()
                .filter(m -> m.getParentId() != null)
                .collect(Collectors.groupingBy(SysMenu::getParentId,
                        Collectors.mapping(SysMenu::getMenuId, Collectors.toList())));

        Set<Long> result = new LinkedHashSet<>();
        Deque<Long> queue = new ArrayDeque<>(childrenOf.getOrDefault(menuId, List.of()));
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (result.add(current)) {
                queue.addAll(childrenOf.getOrDefault(current, List.of()));
            }
        }
        return result;
    }

    private SysMenu requireMenu(Long menuId) {
        BizException.throwIf(menuId == null, "菜单ID不能为空");
        SysMenu menu = menuMapper.selectById(menuId);
        BizException.throwIf(menu == null, "菜单不存在");
        return menu;
    }

    private void validate(SysMenuDTO dto) {
        BizException.throwIf(StrUtil.isBlank(dto.getMenuName()), "菜单名称不能为空");
        BizException.throwIf(StrUtil.isBlank(dto.getMenuType()), "菜单类型不能为空");
        BizException.throwIf(!MENU_TYPES.contains(dto.getMenuType()), "菜单类型不合法：M=目录 C=菜单 F=按钮");
    }

    /** 把扁平菜单列表组装成树 */
    private List<SysMenuTreeVO> buildTree(List<SysMenu> menus) {
        Map<Long, SysMenuTreeVO> nodes = new LinkedHashMap<>();
        for (SysMenu menu : menus) {
            nodes.put(menu.getMenuId(), toTreeVO(menu));
        }
        List<SysMenuTreeVO> roots = new ArrayList<>();
        for (SysMenuTreeVO node : nodes.values()) {
            SysMenuTreeVO parent = node.getParentId() == null ? null : nodes.get(node.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    private SysMenuTreeVO toTreeVO(SysMenu menu) {
        SysMenuTreeVO vo = new SysMenuTreeVO();
        vo.setMenuId(menu.getMenuId());
        vo.setParentId(menu.getParentId());
        vo.setMenuName(menu.getMenuName());
        vo.setOrderNum(menu.getOrderNum());
        vo.setPath(menu.getPath());
        vo.setComponent(menu.getComponent());
        vo.setQuery(menu.getQuery());
        vo.setIsFrame(menu.getIsFrame());
        vo.setIsCache(menu.getIsCache());
        vo.setMenuType(menu.getMenuType());
        vo.setVisible(menu.getVisible());
        vo.setStatus(menu.getStatus());
        vo.setPerms(menu.getPerms());
        vo.setIcon(menu.getIcon());
        return vo;
    }
}
