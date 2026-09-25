package com.hparty.common.enums;

import lombok.Getter;

/**
 * 党组织类型。
 */
@Getter
public enum OrgType {

    PARTY_COMMITTEE(1, "党委"),
    GENERAL_BRANCH(2, "党总支"),
    BRANCH(3, "党支部"),
    PARTY_GROUP(4, "党小组"),

    /**
     * 管理节点：超级管理员的归属组织，<b>不是真实的党组织</b>。
     *
     * <p>它存在的唯一理由是 {@code sys_user.org_id} 会被写入各类业务表的 NOT NULL 组织列，
     * 而超管本身没有党组织；若为 NULL 会直接撞数据库约束报 SQL 异常。因此单开一个
     * {@code parent_id = 0} 的独立根节点作为其归属，使真实党组织保持单棵树
     * （{@code DevPlanMapper#selectRootOrgId} 等依赖「根组织只有一个」的假设不受影响）。</p>
     *
     * <p>该类型<b>只能由 Flyway 迁移创建</b>：{@code SysDeptService.validate()} 仍只放行
     * 1–4，界面无法新增或改成 9。所有对外展示的组织列表、组织树、下拉与统计口径
     * 一律排除本类型，调用方不要把它当作党组织参与业务计算。</p>
     */
    ADMIN_NODE(9, "管理节点");

    private final int code;
    private final String label;

    OrgType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static String labelOf(Integer code) {
        if (code == null) {
            return "";
        }
        for (OrgType t : values()) {
            if (t.code == code) {
                return t.label;
            }
        }
        return "";
    }
}
