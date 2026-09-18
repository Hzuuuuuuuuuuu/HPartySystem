# PARTY_SECRETARY Permission Seed Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 Flyway V3 权限修复迁移，使内置 `PARTY_SECRETARY` 恢复“除系统管理外全部党建业务权限”的设计语义，并用永久回归测试与真实账号验证防止再次漂移。

**Architecture:** 先增加数据库集成回归测试，直接断言角色-菜单关系；在当前 version=2 BASELINE 数据库上确认测试失败。随后新增单向、只增不删的 V3 Flyway 迁移，通过 `role_key='PARTY_SECRETARY'` 定位角色，并按菜单树排除系统管理子树，仅例外保留 `menu_id=1204` 党组织管理只读入口。迁移完成后重跑测试、重建 fat-jar、重启后端并通过 `zgq` 登录返回的权限集合做运行态验证。

**Tech Stack:** Java 17, Spring Boot 3.2.5, JUnit 5, Spring Boot Test, JdbcTemplate, MySQL 8, Flyway 9.22.3, Sa-Token 1.38, Maven.

## Global Constraints

- 不修改已经存在的 `V1__init_schema.sql` 与 `V2__init_system_data.sql`。
- 新迁移必须命名为 `V3__repair_party_secretary_permissions.sql`。
- 只修内置 `PARTY_SECRETARY`，不重置其他内置角色和自定义角色。
- 迁移只补缺失 `sys_role_menu` 关联，不删除现有授权。
- `PARTY_SECRETARY` 不获得系统用户、角色、菜单、字典、日志管理权限。
- `PARTY_SECRETARY` 保留 `system:dept:list`，但不获得 `system:dept:add/edit/remove`。
- 当前项目根目录没有 `.git` 元数据，因此本计划不执行 commit/worktree 步骤；每个任务以 AgentDock checkpoint 代替恢复点。

---

### Task 1: 添加角色权限种子回归测试并确认 RED

**Files:**
- Create: `hparty-server/hparty-admin/src/test/java/com/hparty/security/RolePermissionSeedRegressionTest.java`

**Interfaces:**
- Consumes: `sys_role`, `sys_menu`, `sys_role_menu` 当前数据库结构；Spring Boot Test 已有 dev 数据源/Flyway 配置。
- Produces: 永久回归测试 `RolePermissionSeedRegressionTest`，为 V3 迁移提供可重复验收契约。

- [ ] **Step 1: 创建回归测试**

```java
package com.hparty.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "hparty.captcha.enabled=false")
@ActiveProfiles("dev")
class RolePermissionSeedRegressionTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void partySecretaryHasBusinessPermissionsButNotSystemAdministrationWrites() {
        assertThat(hasPermission("develop:applicant:handle")).isTrue();
        assertThat(hasPermission("develop:applicant:detail")).isTrue();
        assertThat(hasPermission("partyday:add")).isTrue();
        assertThat(hasPermission("system:dept:list")).isTrue();

        assertThat(hasPermission("system:user:add")).isFalse();
        assertThat(hasPermission("system:role:add")).isFalse();
        assertThat(hasPermission("system:menu:add")).isFalse();
        assertThat(hasPermission("system:dict:add")).isFalse();
        assertThat(hasPermission("system:dept:add")).isFalse();
    }

    @Test
    void roleMenuPairsRemainUnique() {
        Integer duplicates = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM (
                    SELECT rm.role_id, rm.menu_id
                    FROM sys_role_menu rm
                    JOIN sys_role r ON r.role_id = rm.role_id
                    WHERE r.role_key = 'PARTY_SECRETARY'
                      AND r.is_builtin = 1
                    GROUP BY rm.role_id, rm.menu_id
                    HAVING COUNT(*) > 1
                ) duplicated
                """, Integer.class);
        assertThat(duplicates).isZero();
    }

    private boolean hasPermission(String permission) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM sys_role r
                JOIN sys_role_menu rm ON rm.role_id = r.role_id
                JOIN sys_menu m ON m.menu_id = rm.menu_id
                WHERE r.role_key = 'PARTY_SECRETARY'
                  AND r.is_builtin = 1
                  AND r.status = 1
                  AND r.del_flag = 0
                  AND m.status = 1
                  AND m.perms = ?
                """, Integer.class, permission);
        return count != null && count > 0;
    }
}
```

- [ ] **Step 2: 仅运行该测试，确认迁移前失败**

Run:

```powershell
mvn -f hparty-server/hparty-admin/pom.xml -Dtest=RolePermissionSeedRegressionTest test
```

Expected: `partySecretaryHasBusinessPermissionsButNotSystemAdministrationWrites` FAIL；至少 `develop:applicant:handle` 或 `partyday:add` 的 `isTrue()` 断言失败。`roleMenuPairsRemainUnique` 应 PASS。

- [ ] **Step 3: 记录迁移前数据库状态**

Run:

```sql
SELECT version, description, type, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT m.menu_id, m.menu_name, m.perms,
       EXISTS(
         SELECT 1 FROM sys_role_menu rm
         JOIN sys_role r ON r.role_id=rm.role_id
         WHERE r.role_key='PARTY_SECRETARY'
           AND r.is_builtin=1
           AND rm.menu_id=m.menu_id
       ) AS granted
FROM sys_menu m
WHERE m.perms IN (
  'develop:applicant:handle',
  'develop:applicant:detail',
  'partyday:add',
  'system:dept:list',
  'system:dept:add',
  'system:user:add'
)
ORDER BY m.menu_id;
```

Expected: Flyway 当前为 version 2 BASELINE；业务按钮至少有缺失，系统管理写权限保持未授权。

---

### Task 2: 新增 V3 只增不删权限修复迁移

**Files:**
- Create: `hparty-server/hparty-admin/src/main/resources/db/migration/V3__repair_party_secretary_permissions.sql`
- Test: `hparty-server/hparty-admin/src/test/java/com/hparty/security/RolePermissionSeedRegressionTest.java`

**Interfaces:**
- Consumes: Task 1 的权限契约；`sys_menu.parent_id` 菜单树；`sys_role.role_key/is_builtin/status/del_flag`。
- Produces: Flyway version 3；对所有内置 `PARTY_SECRETARY` 实例补齐缺失业务菜单关系。

- [ ] **Step 1: 创建 V3 迁移**

```sql
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
```

- [ ] **Step 2: 先在 MySQL 中用只读 SELECT 预演目标集合**

Run equivalent read-only query replacing the final INSERT with:

```sql
WITH RECURSIVE system_admin_tree AS (
    SELECT menu_id FROM sys_menu WHERE menu_id = 12
    UNION ALL
    SELECT child.menu_id
    FROM sys_menu child
    JOIN system_admin_tree parent ON child.parent_id = parent.menu_id
)
SELECT m.menu_id, m.menu_name, m.perms
FROM sys_menu m
WHERE m.status = 1
  AND (m.menu_id = 1204 OR m.menu_id NOT IN (SELECT menu_id FROM system_admin_tree))
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_menu rm
      JOIN sys_role r ON r.role_id = rm.role_id
      WHERE r.role_key='PARTY_SECRETARY'
        AND r.is_builtin=1
        AND rm.menu_id=m.menu_id
  )
ORDER BY m.menu_id;
```

Expected: 包含 `3011..3016`、`2011..2013` 等缺失业务按钮；不包含 `12011..12072` 系统管理按钮，也不包含 `12041..12043` 党组织管理写按钮。

- [ ] **Step 3: 停止当前后端后执行测试，让 Flyway 自动应用 V3**

先确认并停止占用 8080 的 Java 后端，避免 Windows `clean`/重打包锁文件；随后运行：

```powershell
mvn -f hparty-server/hparty-admin/pom.xml -Dtest=RolePermissionSeedRegressionTest test
```

Expected: Spring Boot 启动日志显示 `Migrating schema hparty to version "3 - repair party secretary permissions"`；两个测试均 PASS。

- [ ] **Step 4: 数据库验证迁移结果和边界**

Run:

```sql
SELECT version, description, type, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT m.perms
FROM sys_role r
JOIN sys_role_menu rm ON rm.role_id=r.role_id
JOIN sys_menu m ON m.menu_id=rm.menu_id
WHERE r.role_key='PARTY_SECRETARY'
  AND r.is_builtin=1
  AND m.perms IN (
    'develop:applicant:handle',
    'develop:applicant:detail',
    'partyday:add',
    'system:dept:list',
    'system:dept:add',
    'system:user:add'
  )
ORDER BY m.perms;
```

Expected: version 3 SUCCESS；包含 `develop:applicant:handle`、`develop:applicant:detail`、`partyday:add`、`system:dept:list`；不包含 `system:dept:add`、`system:user:add`。

---

### Task 3: 全量验证、运行态验证与报告同步

**Files:**
- Modify: `docs/08-全系统回归巡检报告.md`
- Verify: `hparty-server/hparty-admin/src/main/resources/db/migration/V3__repair_party_secretary_permissions.sql`
- Verify: `hparty-server/hparty-admin/src/test/java/com/hparty/security/RolePermissionSeedRegressionTest.java`

**Interfaces:**
- Consumes: 已应用的 Flyway V3、现有 `zgq` demo 党委书记账号、现有 Sa-Token 登录接口。
- Produces: 完整构建结果、运行态权限证据、更新后的巡检状态。

- [ ] **Step 1: 运行完整后端测试**

Run:

```powershell
mvn -f hparty-server/pom.xml test
```

Expected: `DataScopeHelperTest` 5/5、发展党员规则 67/67、`DataScopeEndpointRegressionTest` 2/2、`RolePermissionSeedRegressionTest` 2/2 均通过，Reactor `BUILD SUCCESS`。

- [ ] **Step 2: clean install 重建最新 fat-jar**

Run:

```powershell
mvn -f hparty-server/pom.xml clean install -DskipTests
```

Expected: Reactor `BUILD SUCCESS`，生成 `hparty-server/hparty-admin/target/hparty-admin.jar`。

- [ ] **Step 3: 启动后端并验证基础健康**

Run:

```powershell
java -jar hparty-server/hparty-admin/target/hparty-admin.jar
```

Expected: 8080 正常监听；Flyway 显示 schema current version=3 且无需新迁移；`GET /api/auth/captcha` HTTP 200；5173 前端及 `/api` 代理仍正常。

- [ ] **Step 4: 使用 `zgq` 真实账号验证权限集合**

在 captcha 可关闭的本地 dev 配置或通过合法验证码登录 `zgq`，调用登录/用户信息接口，断言权限集合：

```text
develop:applicant:handle  -> present
develop:applicant:detail  -> present
partyday:add               -> present
system:dept:list           -> present
system:user:add            -> absent
system:dept:add            -> absent
```

Expected: 权限集合符合上述边界；STEP_04 不再因 `@SaCheckPermission("develop:applicant:handle")` 被直接 403 拒绝。

- [ ] **Step 5: 更新巡检报告**

在 `docs/08-全系统回归巡检报告.md` 追加 V3 修复记录，明确：

```text
- 党委书记权限种子漂移 P0：已修复
- Flyway：2 BASELINE -> 3 SUCCESS
- 实际新增 PARTY_SECRETARY 角色菜单关联数量
- develop:applicant:handle 已恢复
- 系统管理写权限未扩大
- RolePermissionSeedRegressionTest 2/2
- 完整 Maven 测试 BUILD SUCCESS
- zgq 运行态权限集合验证通过
```

- [ ] **Step 6: 最终状态复核**

确认 V1/V2 文件时间/内容未修改；V3 已进入 `flyway_schema_history`；MySQL 3306、Redis 6379、后端 8080、前端 5173 均正常；无临时测试用户/数据残留。
