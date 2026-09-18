-- 修复已有库在 V2 baseline 后未同步到的 PARTY_SECRETARY 权限。
-- 设计语义：党委书记拥有除“系统管理”子树外的全部党建业务权限，
-- 但保留党组织管理（menu_id=1204）只读入口。
-- 仅补缺失关联，不删除/覆盖任何现有授权。

INSERT INTO sys_role_menu (role_id, menu_id)
WITH RECURSIVE system_admin_tree AS (
    SELECT menu_id
    FROM sys_menu
    WHERE menu_id = 12

    UNION ALL

    SELECT child.menu_id
    FROM sys_menu child
    JOIN system_admin_tree parent ON child.parent_id = parent.menu_id
),
target_permissions AS (
    SELECT r.role_id, m.menu_id
    FROM sys_role r
    CROSS JOIN sys_menu m
    WHERE r.role_key = 'PARTY_SECRETARY'
      AND r.is_builtin = 1
      AND r.status = 1
      AND r.del_flag = 0
      AND m.status = 1
      AND (
          m.menu_id = 1204
          OR m.menu_id NOT IN (SELECT menu_id FROM system_admin_tree)
      )
)
SELECT target.role_id, target.menu_id
FROM target_permissions target
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_role_menu existing
    WHERE existing.role_id = target.role_id
      AND existing.menu_id = target.menu_id
);
