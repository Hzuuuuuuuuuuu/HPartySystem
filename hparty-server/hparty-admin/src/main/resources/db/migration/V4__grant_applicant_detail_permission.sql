-- =====================================================================
-- V4 — 入党申请人本人发展流程详情只读权限
--
-- APPLICANT 内置角色的数据范围是 SELF。V2 只授了 develop:applicant:list，
-- 前端列表能看到本人卡片，但进入 25 步详情会被功能权限直接 403。
--
-- 本迁移只补 detail，不授 handle/add/edit/remove/export；最终可见范围仍由
-- DataScopeHelper.canAccessData(org_id, person_id) 的 SELF 校验限制为本人。
-- =====================================================================

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.perms = 'develop:applicant:detail'
WHERE r.role_key = 'APPLICANT'
  AND r.is_builtin = 1
  AND r.status = 1
  AND r.del_flag = 0
  AND m.status = 1
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu rm
      WHERE rm.role_id = r.role_id
        AND rm.menu_id = m.menu_id
  );
