-- =====================================================================
-- V5 — 发展党员材料提交与申请人本人提交专用权限
--
-- 目标：
-- 1) APPLICANT 只获得本人提交入口，不授予通用 develop:applicant:handle；
-- 2) 支部/党委管理角色获得材料管理功能权限；
-- 3) 真正的 applicant/template/submit_role/data-scope 资源级授权仍由服务端执行。
-- =====================================================================

INSERT INTO sys_menu
(menu_id, parent_id, menu_name, order_num, path, component, menu_type, visible, status, perms, icon)
SELECT 3017, 301, '材料管理', 7, NULL, NULL, 'F', 1, 1, 'develop:material:submit', NULL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE perms = 'develop:material:submit'
);

INSERT INTO sys_menu
(menu_id, parent_id, menu_name, order_num, path, component, menu_type, visible, status, perms, icon)
SELECT 3018, 301, '本人提交', 8, NULL, NULL, 'F', 1, 1, 'develop:applicant:self-submit', NULL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE perms = 'develop:applicant:self-submit'
);

-- 支部/党委管理角色获得材料管理功能闸门。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.perms = 'develop:material:submit'
WHERE r.role_key IN ('PARTY_SECRETARY', 'BRANCH_SECRETARY', 'BRANCH_DEPUTY', 'ORG_COMMITTEE')
  AND r.is_builtin = 1
  AND r.status = 1
  AND r.del_flag = 0
  AND m.status = 1
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu rm
      WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
  );

-- APPLICANT 只获得本人提交权限；不授予 develop:material:submit / develop:applicant:handle。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.perms = 'develop:applicant:self-submit'
WHERE r.role_key = 'APPLICANT'
  AND r.is_builtin = 1
  AND r.status = 1
  AND r.del_flag = 0
  AND m.status = 1
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu rm
      WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
  );
