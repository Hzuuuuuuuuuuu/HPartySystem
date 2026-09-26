package com.hparty.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hparty.common.constant.Constants;
import com.hparty.common.core.PageResult;
import com.hparty.common.enums.OrgType;
import com.hparty.common.exception.BizException;
import com.hparty.framework.core.PageUtils;
import com.hparty.framework.datascope.DataScopeHelper;
import com.hparty.system.domain.dto.SysDeptQuery;
import com.hparty.system.domain.entity.PartyPerson;
import com.hparty.system.domain.entity.SysDept;
import com.hparty.system.domain.vo.SysDeptTreeVO;
import com.hparty.system.mapper.PartyPersonMapper;
import com.hparty.system.mapper.SysDeptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 党组织服务。
 *
 * <h3>物化路径维护</h3>
 * <p>组织树同时冗余两个字段：</p>
 * <ul>
 *   <li>{@code org_path} 形如 {@code /1/3/7/}，供数据权限做前缀匹配（{@code LIKE '/1/3/%'}）</li>
 *   <li>{@code ancestors} 形如 {@code 0,1,3}，供前端/报表展示祖级链路</li>
 * </ul>
 * <p>两者必须成对维护。新增时先落库拿到自增主键，再回填路径；修改父节点时，
 * 除本节点外还要按旧路径前缀捞出全部子孙，整体重写它们的 {@code org_path} 与
 * {@code ancestors}，否则数据权限会漏掉整棵子树。</p>
 */
@Service
@RequiredArgsConstructor
public class SysDeptService {

    /** 根节点的祖级列表 */
    private static final String ROOT_ANCESTORS = "0";

    private final SysDeptMapper deptMapper;
    private final PartyPersonMapper personMapper;

    // ==================== 查询 ====================

    /**
     * 党组织分页列表。
     * <p>SysDept 的行标识就是 org_id，数据权限条件落在 org_id 上即「我能看到哪些组织」。</p>
     *
     * @param query 过滤条件：orgName 模糊、orgType、status
     * @return 分页结果
     */
    public PageResult<SysDept> listDept(SysDeptQuery query) {
        QueryWrapper<SysDept> wrapper = new QueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(query.getOrgName()), "org_name", query.getOrgName());
        wrapper.eq(query.getOrgType() != null, "org_type", query.getOrgType());
        wrapper.eq(query.getStatus() != null, "status", query.getStatus());
        wrapper.eq(query.getParentId() != null, "parent_id", query.getParentId());
        // 管理节点（超管归属，OrgType.ADMIN_NODE）不是党组织，不对外展示。
        // 这里是 SELECT，`.ne` 不受 BlockAttackInnerInterceptor 的 UPDATE 限制。
        wrapper.ne("org_type", OrgType.ADMIN_NODE.getCode());

        // sys_dept 的行标识就是 org_id，没有 person_id 列，personColumn 传 null
        // （否则「仅本人」数据范围的用户会生成 WHERE person_id = ? 而报 Unknown column）
        DataScopeHelper.apply(wrapper, "org_id", null);

        PageUtils.applyOrder(wrapper, query);
        if (StrUtil.isBlank(query.getOrderByColumn())) {
            wrapper.orderByAsc("order_num");
            wrapper.orderByAsc("org_id");
        }

        Page<SysDept> page = PageUtils.toPage(query);
        return PageResult.of(deptMapper.selectPage(page, wrapper));
    }

    /**
     * 组织架构树（图 4）。
     * <p>返回全量党组织，供组织架构图渲染与「上级组织」下拉选择使用，不做数据权限过滤 ——
     * 树一旦被裁剪，父节点缺失会导致整棵子树挂不上根。需要按权限看数据时请使用
     * {@link #listDept(SysDeptQuery)}。</p>
     *
     * <p>管理节点（{@link OrgType#ADMIN_NODE}）被排除：它不是党组织，也不该被选作
     * 任何组织的上级，否则真实组织会挂到它下面、破坏单棵党组织树。</p>
     *
     * @return 根节点集合
     */
    public List<SysDeptTreeVO> listDeptTree() {
        List<SysDept> depts = deptMapper.selectList(new QueryWrapper<SysDept>()
                .ne("org_type", OrgType.ADMIN_NODE.getCode())
                .orderByAsc("order_num")
                .orderByAsc("org_id"));
        return buildTree(depts);
    }

    /**
     * 党组织详情。
     *
     * @param orgId 组织 ID
     * @return 组织实体
     */
    public SysDept getDept(Long orgId) {
        SysDept dept = requireDept(orgId);
        checkAccess(orgId);
        return dept;
    }

    // ==================== 新增 / 修改 ====================

    /**
     * 新增党组织，落库后回填 orgPath 与 ancestors。
     *
     * @param dept 组织信息，parentId 为空时挂到根
     * @return 新增的组织 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long addDept(SysDept dept) {
        validate(dept);
        Long parentId = dept.getParentId() == null ? Constants.ROOT_ORG_ID : dept.getParentId();
        dept.setParentId(parentId);
        if (!isRoot(parentId)) {
            SysDept parent = deptMapper.selectById(parentId);
            BizException.throwIf(parent == null, "上级党组织不存在");
            checkAccess(parentId);
        }

        dept.setOrgLevel(dept.getOrgType());
        if (dept.getStatus() == null) {
            dept.setStatus(Constants.STATUS_NORMAL);
        }
        if (dept.getMemberCount() == null) {
            dept.setMemberCount(0);
        }
        if (dept.getOrderNum() == null) {
            dept.setOrderNum(0);
        }
        // 先给一个占位路径，主键生成后才能算出真实路径
        dept.setOrgPath("/");
        dept.setAncestors(ROOT_ANCESTORS);

        deptMapper.insert(dept);

        fillPath(dept);
        SysDept patch = new SysDept();
        patch.setOrgId(dept.getOrgId());
        patch.setOrgPath(dept.getOrgPath());
        patch.setAncestors(dept.getAncestors());
        patch.setOrgLevel(dept.getOrgLevel());
        deptMapper.updateById(patch);
        return dept.getOrgId();
    }

    /**
     * 修改党组织。父节点发生变化时级联重写整棵子树的 orgPath 与 ancestors。
     *
     * @param dept 组织信息，orgId 必填
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateDept(SysDept dept) {
        BizException.throwIf(dept.getOrgId() == null, "组织ID不能为空");
        SysDept old = requireDept(dept.getOrgId());
        checkAccess(old.getOrgId());
        validate(dept);

        Long newParentId = dept.getParentId() == null ? old.getParentId() : dept.getParentId();
        dept.setParentId(newParentId);

        String oldPath = old.getOrgPath();
        String oldAncestors = old.getAncestors();
        boolean parentChanged = !Objects.equals(newParentId, old.getParentId());

        String newPath = oldPath;
        String newAncestors = oldAncestors;
        if (parentChanged) {
            BizException.throwIf(Objects.equals(newParentId, dept.getOrgId()), "上级党组织不能是自己");
            if (isRoot(newParentId)) {
                newAncestors = ROOT_ANCESTORS;
                newPath = wrapPath(dept.getOrgId());
            } else {
                SysDept parent = deptMapper.selectById(newParentId);
                BizException.throwIf(parent == null, "上级党组织不存在");
                BizException.throwIf(StrUtil.isNotBlank(oldPath) && StrUtil.isNotBlank(parent.getOrgPath())
                                && parent.getOrgPath().startsWith(oldPath),
                        "上级党组织不能是自己的下级");
                newAncestors = parent.getAncestors() + "," + newParentId;
                newPath = parent.getOrgPath() + dept.getOrgId() + "/";
            }
            dept.setOrgPath(newPath);
            dept.setAncestors(newAncestors);
        } else {
            // 父节点未变：路径交由数据库保持原值，拒绝客户端传入的路径字段
            dept.setOrgPath(null);
            dept.setAncestors(null);
        }

        dept.setOrgLevel(dept.getOrgType() == null ? old.getOrgType() : dept.getOrgType());
        // 审计字段由 MetaObjectHandler 填充，不接受客户端传入
        dept.setCreateBy(null);
        dept.setCreateTime(null);
        dept.setDelFlag(null);

        deptMapper.updateById(dept);

        if (parentChanged) {
            cascadeUpdateChildren(dept.getOrgId(), oldPath, oldAncestors, newPath, newAncestors);
        }
    }

    /**
     * 删除党组织。存在下级组织或关联人员档案时拒绝删除。
     *
     * @param orgId 组织 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeDept(Long orgId) {
        requireDept(orgId);
        checkAccess(orgId);

        long childCount = deptMapper.selectCount(new QueryWrapper<SysDept>().eq("parent_id", orgId));
        BizException.throwIf(childCount > 0, "存在下级党组织，不允许删除");

        long personCount = personMapper.selectCount(new QueryWrapper<PartyPerson>().eq("org_id", orgId));
        BizException.throwIf(personCount > 0, "该党组织下存在人员档案，不允许删除");

        deptMapper.deleteById(orgId);
    }

    // ==================== 内部方法 ====================

    /**
     * 级联重写子孙节点的物化路径与祖级列表。
     * <p>依赖「子孙的 ancestors 必然以本节点的 ancestors 为前缀」这一不变量，
     * 只需做前缀替换，无需逐层递归。</p>
     */
    private void cascadeUpdateChildren(Long orgId, String oldPath, String oldAncestors,
                                       String newPath, String newAncestors) {
        if (StrUtil.isBlank(oldPath)) {
            return;
        }
        List<SysDept> children = deptMapper.selectList(new QueryWrapper<SysDept>()
                .likeRight("org_path", oldPath)
                .ne("org_id", orgId));
        for (SysDept child : children) {
            SysDept patch = new SysDept();
            patch.setOrgId(child.getOrgId());

            String childPath = child.getOrgPath();
            if (StrUtil.isNotBlank(childPath) && childPath.startsWith(oldPath)) {
                patch.setOrgPath(newPath + childPath.substring(oldPath.length()));
            }
            String childAncestors = child.getAncestors();
            if (StrUtil.isNotBlank(childAncestors) && childAncestors.startsWith(oldAncestors)) {
                patch.setAncestors(newAncestors + childAncestors.substring(oldAncestors.length()));
            }
            deptMapper.updateById(patch);
        }
    }

    /** 依据父节点计算本节点的 orgPath 与 ancestors */
    private void fillPath(SysDept dept) {
        Long parentId = dept.getParentId();
        if (isRoot(parentId)) {
            dept.setAncestors(ROOT_ANCESTORS);
            dept.setOrgPath(wrapPath(dept.getOrgId()));
            return;
        }
        SysDept parent = deptMapper.selectById(parentId);
        BizException.throwIf(parent == null, "上级党组织不存在");
        dept.setAncestors(parent.getAncestors() + "," + parentId);
        dept.setOrgPath(parent.getOrgPath() + dept.getOrgId() + "/");
    }

    /** 拼物化路径：/1/3/7/ */
    private String wrapPath(Long orgId) {
        return "/" + orgId + "/";
    }

    private boolean isRoot(Long parentId) {
        return parentId == null || Objects.equals(parentId, Constants.ROOT_ORG_ID);
    }

    private SysDept requireDept(Long orgId) {
        BizException.throwIf(orgId == null, "组织ID不能为空");
        SysDept dept = deptMapper.selectById(orgId);
        BizException.throwIf(dept == null, "党组织不存在");
        // 管理节点由 Flyway 迁移维护（OrgType.ADMIN_NODE），不能在界面上查看、改名或删除。
        // 尤其删除：它会带走超管的归属组织，让超管的新增业务数据重新撞 NOT NULL 约束。
        BizException.throwIf(Objects.equals(dept.getOrgType(), OrgType.ADMIN_NODE.getCode()),
                "管理节点由系统维护，不可查看、修改或删除");
        return dept;
    }

    private void checkAccess(Long orgId) {
        BizException.throwForbiddenIf(!DataScopeHelper.canAccessOrg(orgId), "无权操作其他党组织的数据");
    }

    private void validate(SysDept dept) {
        BizException.throwIf(StrUtil.isBlank(dept.getOrgName()), "组织名称不能为空");
        BizException.throwIf(dept.getOrgType() == null, "组织类型不能为空");
        BizException.throwIf(dept.getOrgType() < 1 || dept.getOrgType() > 4,
                "组织类型不合法：1=党委 2=党总支 3=党支部 4=党小组");
    }

    /** 把扁平组织列表组装成树 */
    private List<SysDeptTreeVO> buildTree(List<SysDept> depts) {
        Map<Long, String> secretaryNames = loadSecretaryNames(depts);
        Map<Long, SysDeptTreeVO> nodes = new LinkedHashMap<>();
        for (SysDept dept : depts) {
            nodes.put(dept.getOrgId(), toTreeVO(dept, secretaryNames));
        }
        List<SysDeptTreeVO> roots = new ArrayList<>();
        for (SysDeptTreeVO node : nodes.values()) {
            SysDeptTreeVO parent = node.getParentId() == null ? null : nodes.get(node.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    /** 批量取书记姓名，避免逐行查库 */
    private Map<Long, String> loadSecretaryNames(List<SysDept> depts) {
        Set<Long> personIds = depts.stream()
                .map(SysDept::getSecretaryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (personIds.isEmpty()) {
            return Map.of();
        }
        List<PartyPerson> persons = personMapper.selectList(new QueryWrapper<PartyPerson>()
                .select("person_id", "name")
                .in("person_id", personIds));
        return persons.stream().collect(Collectors.toMap(
                PartyPerson::getPersonId, PartyPerson::getName, (a, b) -> a));
    }

    private SysDeptTreeVO toTreeVO(SysDept dept, Map<Long, String> secretaryNames) {
        SysDeptTreeVO vo = new SysDeptTreeVO();
        vo.setOrgId(dept.getOrgId());
        vo.setParentId(dept.getParentId());
        vo.setOrgName(dept.getOrgName());
        vo.setOrgType(dept.getOrgType());
        vo.setOrgTypeLabel(OrgType.labelOf(dept.getOrgType()));
        vo.setOrgLevel(dept.getOrgLevel());
        vo.setLeader(dept.getLeader());
        vo.setMemberCount(dept.getMemberCount());
        vo.setSecretaryName(dept.getSecretaryId() == null ? null : secretaryNames.get(dept.getSecretaryId()));
        vo.setFoundedDate(dept.getFoundedDate());
        return vo;
    }
}
