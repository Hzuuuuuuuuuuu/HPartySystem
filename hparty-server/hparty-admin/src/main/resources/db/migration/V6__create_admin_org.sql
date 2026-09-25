-- =====================================================================
-- V6 — 超级管理员归属组织（管理节点）
--
-- 背景：
-- 超管 admin 的 sys_user.org_id 为 NULL（它没有归属党组织）。大量业务表把
-- 「当前登录人的 orgId」写入 NOT NULL 的组织列（am_meeting / am_material /
-- am_task / am_task_submit / discipline_study / edu_activity /
-- excellent_selection / member_service / org_election / party_dues_record /
-- party_dues_use …），MyBatis-Plus 默认 insert 策略会省略 null 字段，
-- 于是 MySQL 报 1364 "Field 'org_id' doesn't have a default value"，
-- 前端只拿到无信息的 500 —— admin 在 11 个创建入口上全都建不了数据。
--
-- 做法：
-- 为 admin 单开一个「管理节点」作为归属组织。它 org_type = 9，且是
-- parent_id = 0 的独立根，因此**真实党组织仍是单棵树**：
-- DevPlanMapper#selectRootOrgId 等依赖「根组织只有一个」的假设不受影响，
-- 真实组织的数据权限子树也不会把它圈进来。
--
-- 该节点不是党组织，对外一律不展示：
--   · SysDeptService#listDept / #listDeptTree  组织列表与组织树（含下拉）
--   · PartyPersonService 党组织总数统计
--   · DevPlanMapper#selectRootOrgId            取根组织
-- 以上位置均按 org_type = 9 排除。OrgType.ADMIN_NODE 有完整说明。
--
-- 两条约束：
--   1) 该类型只能由本迁移创建 —— SysDeptService.validate() 仍只放行 1–4，
--      界面新增/编辑都造不出 9。
--   2) 只在 admin 尚无归属组织时回写；已上线部署若已把 admin 挂到真实党委，
--      绝不被本迁移改写。
--
-- 不修改已发布的 V1–V5。
-- =====================================================================

-- 组织 ID 从 9999 起取第一个空位。
-- 为什么要避开自增区间：sql/03-init-demo.sql 会 DELETE 后显式插入 org_id 1–3 的组织，
-- 若管理节点在空库上拿到 1，演示脚本就会主键冲突。显式指定高位 ID 的副作用是
-- sys_dept 的自增计数器随之抬到该值之后，属预期行为。
-- 组织路径先留占位符，主键确定后于下方回填（与 SysDeptService#addDept 的两步写法一致）。
INSERT INTO sys_dept
(org_id, parent_id, ancestors, org_path, org_name, org_short_name, org_code, org_type, org_level,
 member_count, order_num, status, del_flag, create_by)
SELECT
    COALESCE((SELECT MAX(org_id) FROM sys_dept WHERE org_id >= 9999), 9998) + 1,
    0, '0', '/', '管理员', '管理员', 'ADMIN_NODE', 9, 1, 0, 0, 1, 0, 'flyway'
WHERE NOT EXISTS (SELECT 1 FROM sys_dept WHERE org_type = 9);

-- 回填物化路径与祖级列表。org_path 仍为占位符 '/' 的行即本次新建的节点。
UPDATE sys_dept
SET org_path = CONCAT('/', org_id, '/'),
    ancestors = '0',
    org_level = 1
WHERE org_type = 9
  AND org_path = '/';

-- 把管理节点指派给超管，但只在它还没有归属组织时。
UPDATE sys_user
SET org_id = (SELECT org_id FROM sys_dept WHERE org_type = 9 ORDER BY org_id LIMIT 1)
WHERE username = 'admin'
  AND org_id IS NULL;

-- 结果自查：应为 1 个管理节点、admin 的 org_id 非空。
SELECT
    (SELECT COUNT(*) FROM sys_dept WHERE org_type = 9)            AS 管理节点数,
    (SELECT org_id FROM sys_user WHERE username = 'admin')        AS admin所属组织;
