# PARTY_SECRETARY 权限种子漂移修复设计

## 1. 背景

当前数据库通过 Flyway `version=2` BASELINE 接管，V1/V2 并未在该库实际执行。后续对 `V2__init_system_data.sql` 的角色菜单调整不会自动补到现库，因此内置 `PARTY_SECRETARY`（党委书记）与当前 V2 设计发生漂移。

实测当前党委书记可以看到发展党员菜单，但缺少 `develop:applicant:add/edit/remove/handle/detail/export` 等按钮权限，导致 STEP_04 等上级党委办理动作被 `@SaCheckPermission` 直接拒绝。

进一步核对发现，问题不是单个按钮漏授：V2 注释定义党委书记为“除系统管理外全部”，但其主授权条件使用 `menu_id < 1200 OR menu_id IN (1204)`。大量合法党建业务按钮 ID 高于 1200，因此同类漏权还存在于主题党日、换届、组织生活、教育、人员名册、党费、党员服务、党纪学习、评优等模块。

## 2. 目标

新增 Flyway V3+ 迁移，在不修改 V1/V2、不删除任何现有授权、不影响自定义角色的前提下，将内置 `PARTY_SECRETARY` 恢复到 V2 原始设计语义：

- 所有非“系统管理”树下的党建业务菜单和按钮均可访问；
- 系统管理目录及用户、角色、菜单、字典、日志等管理权限不授予党委书记；
- 保留 V2 已明确给党委书记的 `党组织管理`（menu_id=1204）只读入口；不额外扩大到其新增/修改/删除按钮；
- 已存在的权限关联保持不变，仅补缺失项。

## 3. 非目标

本次不处理：

- 其他内置角色的重新建模或权限重构；
- 自定义角色权限；
- 数据权限 `data_scope`；
- APPLICANT 详情入口问题；
- 403 业务码统一；
- 文件预览匿名访问和生产初始密码。

如果迁移审计中发现其他内置角色存在独立漂移，只记录到巡检报告，另开修复，不在本次顺手重置。

## 4. 迁移策略

新增：

`hparty-admin/src/main/resources/db/migration/V3__repair_party_secretary_permissions.sql`

迁移只针对数据库中 `role_key='PARTY_SECRETARY' AND is_builtin=1` 的角色，不依赖固定 `role_id=2`，避免不同环境主键不一致。

### 4.1 目标菜单集合

从当前 `sys_menu` 动态选择：

1. 排除系统管理根目录 `menu_id=12`；
2. 排除系统管理一级菜单 `parent_id=12`，但单独保留 `menu_id=1204`（党组织管理）；
3. 排除系统管理一级菜单的所有按钮子项，即 `parent_id IN (1201,1202,1203,1204,1205,1206,1207)`；
4. 其余菜单、页面和按钮全部属于党委书记的党建业务权限集合。

这样不再用 `menu_id < 1200` 这种与业务语义无关的数值边界。当前以及以后新增的非系统管理业务按钮，只要挂在业务菜单树上，都能符合“除系统管理外全部”的设计语义。

### 4.2 写入方式

使用 `INSERT ... SELECT ... WHERE NOT EXISTS` 只补缺失的 `(role_id, menu_id)`：

- 不 `DELETE`；
- 不全量重建 `sys_role_menu`；
- 不覆盖管理员人工追加的合法权限；
- 即使在不同基线状态的库上执行，也不会产生重复主键关联。

Flyway 正常只执行一次，但 SQL 本身仍设计为幂等补齐逻辑，便于预演和审计。

## 5. 权限边界

迁移后党委书记应至少拥有：

- `develop:applicant:list/add/edit/remove/handle/detail/export`；
- 主题党日、换届、组织生活、教育、人员名册、党费、党员服务、党纪学习、评优等非系统管理业务按钮；
- 现有民主评议、年度计划、组织关系转接权限继续保留；
- `system:dept:list` 保留。

党委书记不应因本迁移获得：

- `system:user:*`；
- `system:role:*`；
- `system:menu:*`；
- `system:dict:*`；
- 登录日志/操作日志管理权限；
- `system:dept:add/edit/remove`（保持 V2 原有“1204 入口只读”的实际边界）。

## 6. 验证设计

### 6.1 迁移前审计

记录：

- `flyway_schema_history` 当前版本；
- PARTY_SECRETARY 当前菜单数量；
- `develop:applicant:*` 六项按钮授权状态；
- 迁移预计新增关联数量。

### 6.2 自动化测试

新增权限种子回归测试，至少验证：

- PARTY_SECRETARY 拥有 `develop:applicant:handle`、`develop:applicant:detail`；
- PARTY_SECRETARY 拥有一个 ID>1200 的非发展党员业务按钮权限，证明修复不是只补六个固定 ID；
- PARTY_SECRETARY 不拥有 `system:user:add`；
- PARTY_SECRETARY 不拥有 `system:dept:add`；
- 重复目标集合中不存在重复 `(role_id, menu_id)`。

同时继续运行现有数据权限测试和发展党员 67 项规则测试。

### 6.3 真实账号验证

迁移成功并重启后，以现有党委书记账号 `zgq` 获取权限集合，确认包含 `develop:applicant:handle`。

随后实际调用一个需要该权限的上级党委办理入口。若当前 demo 流程状态不适合安全执行写操作，则以受事务保护的集成测试完成 Controller → Sa-Token 权限 → Service 链路验证，不人为修改现有演示流程状态。

## 7. 错误与回滚

- Flyway 迁移失败：应用启动必须失败，不能带半迁移状态运行。
- 本迁移仅插入角色菜单关联，不修改业务数据和表结构。
- 如需人工回滚，只删除本次新增且符合本设计目标集合的 PARTY_SECRETARY 关联；但生产上优先通过新版本迁移修正，不修改已应用 V3 文件。

## 8. 文档同步

迁移完成后更新 `docs/08-全系统回归巡检报告.md`：

- 将“党委书记权限种子漂移”P0 标记为已修复；
- 记录 V3 版本、实际新增权限数、测试结果和真实账号验证结果；
- 保留剩余 P0/P1 项的后续顺序。

## 9. 验收标准

- V1/V2 文件未修改；
- Flyway 从 version 2 正常迁移到 version 3；
- PARTY_SECRETARY 的缺失党建业务权限被补齐，且系统管理权限边界未扩大；
- `zgq` 权限集合包含 `develop:applicant:handle`；
- 后端完整测试 BUILD SUCCESS；
- MySQL、Redis、8080、5173 和 Vite `/api` 代理保持正常。
