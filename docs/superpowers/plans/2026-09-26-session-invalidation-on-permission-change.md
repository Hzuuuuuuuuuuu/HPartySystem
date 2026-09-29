# 权限变更后的会话失效 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 当用户、角色或菜单权限发生变化并成功提交后，立即使受影响用户的全部 Sa-Token 会话失效，避免旧权限继续生效。

**Architecture:** 在 `hparty-framework` 增加集中式 `SessionInvalidator`，统一封装 `StpUtil.logout(userId)` 和事务提交后的回调。`hparty-system` 的用户、角色、菜单 Service 通过 `SysRelationMapper` 查询受影响用户 ID，在数据库事务成功提交后调用该组件；不改变前端鉴权协议、数据库结构或权限标识。

**Tech Stack:** Java 17、Spring Boot 3.2.5、Sa-Token 1.38.0、MyBatis-Plus 3.5.7、MyBatis 注解 SQL、JUnit 5 / Spring Boot Test、React/Vite 现有前端。

## Global Constraints

- 模块依赖必须保持 `admin → develop/party/system → framework → common`，`framework` 不得依赖 `system`。
- 数据库不新增字段，不新增 Flyway 迁移；关联表继续通过 `SysRelationMapper` 注解 SQL 操作。
- 会话失效必须在数据库事务成功提交后执行；事务回滚不得注销会话。
- 用户、角色、菜单 Service 不直接散落调用 `StpUtil`，统一调用 `SessionInvalidator`。
- 批量查询传入空集合前必须在 Java 侧短路，不能生成 `IN ()`。
- 不修改 `@SaCheckPermission` 标识和前端权限判断逻辑。
- Windows 下构建前先停止正在运行的后端 JVM，避免生成残缺 jar；本计划只执行 compile/build，不生成可部署 jar。
- 完成后运行 `git diff --check`、后端编译、受影响测试和前端构建，并如实记录结果。

---

## File Map

- **Create:** `hparty-server/hparty-framework/src/main/java/com/hparty/framework/security/SessionInvalidator.java` — 集中式用户会话失效与事务提交回调。
- **Modify:** `hparty-server/hparty-system/src/main/java/com/hparty/system/mapper/SysRelationMapper.java` — 增加角色/菜单到用户 ID 的批量查询。
- **Modify:** `hparty-server/hparty-system/src/main/java/com/hparty/system/service/SysUserService.java` — 用户状态、角色、删除、重置密码后失效自身会话。
- **Modify:** `hparty-server/hparty-system/src/main/java/com/hparty/system/service/SysRoleService.java` — 角色信息、状态、菜单授权、删除后失效角色用户会话。
- **Modify:** `hparty-server/hparty-system/src/main/java/com/hparty/system/service/SysMenuService.java` — 菜单变更或删除后失效拥有该菜单的用户会话。
- **Create:** `hparty-server/hparty-framework/src/test/java/com/hparty/framework/security/SessionInvalidatorTest.java` — 验证立即失效、事务提交后失效和回滚不失效。
- **Create:** `hparty-server/hparty-admin/src/test/java/com/hparty/security/PermissionSessionInvalidationTest.java` — 集成验证用户、角色和菜单变更后的会话失效。
- **Modify:** `docs/superpowers/specs/2026-09-26-session-invalidation-on-permission-change-design.md` — 仅在实现发现设计约束变化时同步；默认不改。

---

### Task 1: Add the centralized session invalidator

**Files:**
- Create: `hparty-server/hparty-framework/src/main/java/com/hparty/framework/security/SessionInvalidator.java`
- Create: `hparty-server/hparty-framework/src/test/java/com/hparty/framework/security/SessionInvalidatorTest.java`

**Interfaces:**
- Produces `SessionInvalidator.invalidateUser(Long userId)`.
- Produces `SessionInvalidator.invalidateUsers(Collection<Long> userIds)`.
- Produces `SessionInvalidator.invalidateUsersAfterCommit(Collection<Long> userIds)`.
- The two collection methods ignore `null` IDs and empty collections, deduplicate IDs, and never emit invalid logout calls.

- [ ] **Step 1: Write the failing tests**

Create tests that mock the static `StpUtil.logout(Object)` call and assert the component behavior:

```java
class SessionInvalidatorTest {
    @Test
    void invalidatesDistinctNonNullUsersImmediatelyWhenNoTransactionIsActive() {
        try (MockedStatic<StpUtil> stp = Mockito.mockStatic(StpUtil.class)) {
            SessionInvalidator invalidator = new SessionInvalidator();

            invalidator.invalidateUsersAfterCommit(List.of(3L, null, 3L, 7L));

            stp.verify(() -> StpUtil.logout(3L));
            stp.verify(() -> StpUtil.logout(7L));
            stp.verifyNoMoreInteractions();
        }
    }

    @Test
    void waitsUntilCommitBeforeInvalidatingUsers() {
        try (MockedStatic<StpUtil> stp = Mockito.mockStatic(StpUtil.class)) {
            SessionInvalidator invalidator = new SessionInvalidator();
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                invalidator.invalidateUsersAfterCommit(List.of(3L));
                stp.verifyNoInteractions();
            });

            stp.verify(() -> StpUtil.logout(3L));
        }
    }

    @Test
    void doesNotInvalidateUsersWhenTransactionRollsBack() {
        try (MockedStatic<StpUtil> stp = Mockito.mockStatic(StpUtil.class)) {
            assertThrows(RuntimeException.class, () -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        invalidator.invalidateUsersAfterCommit(List.of(3L));
                        throw new RuntimeException("rollback");
                    }));

            stp.verifyNoInteractions();
        }
    }
}
```

Use a test transaction manager already available from Spring Boot Test, or a minimal `ResourcelessTransactionManager` test fixture if the module has no transaction manager bean. If static Mockito support is unavailable, add the smallest test-scope Mockito inline dependency in the framework module rather than weakening the behavior assertion.

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
mvn -f hparty-server/pom.xml -pl hparty-framework -am -Dtest=SessionInvalidatorTest test
```

Expected: FAIL because `SessionInvalidator` does not exist yet.

- [ ] **Step 3: Implement the minimal component**

Create a Spring component with this behavior:

```java
@Component
public class SessionInvalidator {
    public void invalidateUser(Long userId) {
        if (userId != null) {
            StpUtil.logout(userId);
        }
    }

    public void invalidateUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .forEach(this::invalidateUser);
    }

    public void invalidateUsersAfterCommit(Collection<Long> userIds) {
        List<Long> normalized = userIds == null ? List.of() : userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            invalidateUsers(normalized);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                invalidateUsers(normalized);
            }
        });
    }
}
```

Catch and log Sa-Token/Redis failures in `afterCommit` so a successful database transaction is not reported as failed; do not throw back into the business transaction. Keep normal `invalidateUser` behavior observable for tests.

- [ ] **Step 4: Run the focused test and verify it passes**

Run:

```bash
mvn -f hparty-server/pom.xml -pl hparty-framework -am -Dtest=SessionInvalidatorTest test
```

Expected: PASS for immediate, after-commit, rollback, null, empty, and duplicate cases.

- [ ] **Step 5: Commit the component**

```bash
git add hparty-server/hparty-framework/src/main/java/com/hparty/framework/security/SessionInvalidator.java hparty-server/hparty-framework/src/test/java/com/hparty/framework/security/SessionInvalidatorTest.java
git commit -m "feat: add transaction-aware session invalidation"
```

---

### Task 2: Add relation queries for affected users

**Files:**
- Modify: `hparty-server/hparty-system/src/main/java/com/hparty/system/mapper/SysRelationMapper.java`
- Create: `hparty-server/hparty-admin/src/test/java/com/hparty/security/PermissionSessionInvalidationTest.java`

**Interfaces:**
- Produces `List<Long> selectUserIdsByRoleId(Long roleId)`.
- Produces `List<Long> selectUserIdsByRoleIds(Collection<Long> roleIds)`.
- Produces `List<Long> selectUserIdsByMenuId(Long menuId)`.
- All SQL queries return distinct user IDs and use only the existing `sys_user_role` / `sys_role_menu` tables.

- [ ] **Step 1: Add mapper-focused test coverage or SQL contract assertions**

Add a test fixture or extend the existing system security regression test to assert these contracts against the test database:

```java
assertThat(relationMapper.selectUserIdsByRoleId(roleId)).containsExactly(userId);
assertThat(relationMapper.selectUserIdsByRoleIds(List.of(roleId))).containsExactly(userId);
assertThat(relationMapper.selectUserIdsByMenuId(menuId)).containsExactly(userId);
assertThat(relationMapper.selectUserIdsByRoleIds(List.of())).isEmpty();
```

The empty-list assertion must be handled by the Service before calling the mapper; do not invoke a dynamic `IN` statement with an empty collection.

- [ ] **Step 2: Run the focused mapper test and verify the missing methods fail to compile**

Run:

```bash
mvn -f hparty-server/pom.xml -pl hparty-admin -am -Dtest=AdminOrgCrudRegressionTest test
```

Expected: the new test code cannot compile until the mapper methods are added, or the SQL assertions fail until the queries are implemented.

- [ ] **Step 3: Add the exact mapper queries**

Add methods equivalent to:

```java
@Select("SELECT user_id FROM sys_user_role WHERE role_id = #{roleId}")
List<Long> selectUserIdsByRoleId(@Param("roleId") Long roleId);

@Select("""
        <script>
        SELECT DISTINCT user_id
        FROM sys_user_role
        WHERE role_id IN
        <foreach collection="roleIds" item="roleId" open="(" separator="," close=")">
            #{roleId}
        </foreach>
        </script>
        """)
List<Long> selectUserIdsByRoleIds(@Param("roleIds") Collection<Long> roleIds);

@Select("""
        SELECT DISTINCT ur.user_id
        FROM sys_user_role ur
        JOIN sys_role_menu rm ON rm.role_id = ur.role_id
        WHERE rm.menu_id = #{menuId}
        """)
List<Long> selectUserIdsByMenuId(@Param("menuId") Long menuId);
```

- [ ] **Step 4: Re-run the focused mapper test**

Run the same test command and expect PASS, with no empty `IN ()` SQL emitted.

- [ ] **Step 5: Commit the mapper changes**

```bash
git add hparty-server/hparty-system/src/main/java/com/hparty/system/mapper/SysRelationMapper.java
git commit -m "feat: query users affected by permission changes"
```

---

### Task 3: Invalidate sessions from user and role services

**Files:**
- Modify: `hparty-server/hparty-system/src/main/java/com/hparty/system/service/SysUserService.java`
- Modify: `hparty-server/hparty-system/src/main/java/com/hparty/system/service/SysRoleService.java`
- Create: `hparty-server/hparty-admin/src/test/java/com/hparty/security/PermissionSessionInvalidationTest.java`

**Interfaces:**
- Both services consume `SessionInvalidator` via constructor injection.
- `SysUserService` invalidates one user after `updateUser`, `changeStatus`, `removeUser`, and `resetPwd` commit.
- `SysRoleService` collects users before role mutation and invalidates that set after `updateRole`, `assignMenus`, `changeStatus`, and `removeRole` commit.

- [ ] **Step 1: Add regression tests for user and role mutation behavior**

Create `hparty-server/hparty-admin/src/test/java/com/hparty/security/PermissionSessionInvalidationTest.java` using the existing admin test configuration. Add these cases:

```java
@Test
void changingUserRoleInvalidatesAllUserSessions() { /* login, mutate role, assert old token is rejected */ }

@Test
void changingRoleMenusInvalidatesEveryUserAssignedToRole() { /* two users share role, both old tokens are rejected */ }

@Test
void changingUserStatusInvalidatesSessionAfterSuccessfulCommit() { /* status update succeeds, old token rejected */ }

@Test
void failedRoleMutationDoesNotInvalidateSession() { /* force validation/transaction failure, old token remains valid */ }
```

Use the existing test application configuration and test database conventions; do not hard-code production credentials in source or command lines.

- [ ] **Step 2: Run the new tests and verify they fail**

Run:

```bash
mvn -f hparty-server/pom.xml -pl hparty-admin -am -Dtest=PermissionSessionInvalidationTest test
```

Expected: FAIL because the services do not yet inject or invoke `SessionInvalidator`.

- [ ] **Step 3: Wire user-service invalidation**

Inject `SessionInvalidator` into `SysUserService`. Add `@Transactional` to `changeStatus` and `resetPwd` so the after-commit callback has a transaction boundary. After successful database writes, call:

```java
sessionInvalidator.invalidateUsersAfterCommit(List.of(userId));
```

For `updateUser`, call it after the user update and optional role replacement. For `removeUser`, capture the ID before deleting and schedule invalidation after the delete. For `resetPwd`, schedule invalidation after the password update. Do not invalidate on validation failures.

- [ ] **Step 4: Wire role-service invalidation**

Inject `SessionInvalidator` into `SysRoleService`. Add `@Transactional` to `changeStatus`. At the start of each mutating operation, collect affected user IDs with `selectUserIdsByRoleId(roleId)`; for `updateRole`, `assignMenus`, and `changeStatus`, schedule those IDs after successful mutation. For `removeRole`, collect before deletion even though current business validation rejects roles still assigned to users.

Deduplicate IDs in the invalidator rather than relying on each Service to do it. Preserve existing built-in role and “role in use cannot delete” validation.

- [ ] **Step 5: Run the tests and verify they pass**

Run:

```bash
mvn -f hparty-server/pom.xml -pl hparty-admin -am -Dtest=PermissionSessionInvalidationTest test
```

Expected: PASS; successful mutations invalidate old sessions, failed mutations do not.

- [ ] **Step 6: Commit user and role integration**

```bash
git add hparty-server/hparty-system/src/main/java/com/hparty/system/service/SysUserService.java hparty-server/hparty-system/src/main/java/com/hparty/system/service/SysRoleService.java
git commit -m "feat: invalidate sessions after user and role changes"
```

---

### Task 4: Invalidate sessions from menu service

**Files:**
- Modify: `hparty-server/hparty-system/src/main/java/com/hparty/system/service/SysMenuService.java`
- Modify: `hparty-server/hparty-admin/src/test/java/com/hparty/security/PermissionSessionInvalidationTest.java`

**Interfaces:**
- `SysMenuService` consumes `SessionInvalidator` and `SysRelationMapper`.
- `updateMenu` invalidates users returned by `selectUserIdsByMenuId(menuId)` after commit.
- `removeMenu` collects affected users before deleting role-menu association and invalidates them after commit.
- `addMenu` does not invalidate sessions because no existing user session has gained or lost access yet.

- [ ] **Step 1: Add failing menu mutation tests**

Add these cases to the regression test:

```java
@Test
void updatingPermissionMenuInvalidatesUsersWithThatMenu() { /* old token rejected after menu permission/status update */ }

@Test
void removingMenuInvalidatesUsersBeforeAssociationCleanup() { /* collect before delete, then old token rejected */ }

@Test
void addingMenuDoesNotInvalidateUnrelatedSessions() { /* existing session remains valid */ }
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
mvn -f hparty-server/pom.xml -pl hparty-admin -am -Dtest=PermissionSessionInvalidationTest test
```

Expected: menu update/delete tests fail because `SysMenuService` does not yet schedule invalidation.

- [ ] **Step 3: Implement menu integration**

Inject `SessionInvalidator` into `SysMenuService` and add `@Transactional` to `updateMenu`. In `updateMenu`, query affected users immediately after validating the menu ID and before updating it. In `removeMenu`, query affected users before deleting `sys_role_menu` rows. After the successful write/delete, call `invalidateUsersAfterCommit(affectedUserIds)`.

Keep the existing child-menu and cycle validation unchanged. Do not add invalidation to `addMenu`.

- [ ] **Step 4: Run the menu regression tests**

Run:

```bash
mvn -f hparty-server/pom.xml -pl hparty-admin -am -Dtest=PermissionSessionInvalidationTest test
```

Expected: PASS for menu update, menu removal, menu addition, and rollback behavior.

- [ ] **Step 5: Commit menu integration**

```bash
git add hparty-server/hparty-system/src/main/java/com/hparty/system/service/SysMenuService.java hparty-server/hparty-admin/src/test/java/com/hparty/security/PermissionSessionInvalidationTest.java
git commit -m "feat: invalidate sessions after menu permission changes"
```

---

### Task 5: Full verification and documentation check

**Files:**
- Modify: none unless verification exposes a required documentation correction.

- [ ] **Step 1: Stop any running backend JVM before clean build**

Run the project-prescribed Windows process check and stop only the HPartySystem Java process if one is running. Do not stop unrelated Docker or Java services.

- [ ] **Step 2: Run the focused regression suite**

```bash
mvn -f hparty-server/pom.xml -pl hparty-admin -am -Dtest=PermissionSessionInvalidationTest test
```

Expected: PASS.

- [ ] **Step 3: Run the complete backend compile**

```bash
mvn -f hparty-server/pom.xml clean compile -DskipTests
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Run the frontend build**

```bash
cd hparty-web
npm run build
```

Expected: TypeScript compilation and Vite build both pass.

- [ ] **Step 5: Check the diff and conflict markers**

```bash
git diff --check
if git grep -n -E '^(<<<<<<<|=======|>>>>>>>)' -- ':!*.png'; then exit 1; else exit 0; fi
git status --short --branch
```

Expected: no whitespace errors, no conflict markers, and only intended source/test files changed. Preserve the existing uncommitted `hparty-web/vite.config.ts` port-proxy change separately; do not include it in permission-session commits unless the user explicitly requests it.

- [ ] **Step 6: Perform the manual permission-flow verification**

Using a non-production development database and the running app:

1. Log in as a user with `develop:step:list` and keep the browser session open.
2. As an administrator, remove or change that user’s role/menu grant.
3. Use the original browser session to request `/api/develop/flow/steps`; expect 401/session-expired behavior rather than stale permission behavior.
4. Log in again; verify the new menu and API permission set.
5. Re-run with a failed mutation and verify the old session remains usable.

- [ ] **Step 7: Commit only verification/documentation adjustments**

If no adjustments are required, do not create an empty commit. If a code or test correction is required, commit it with a focused message such as:

```bash
git add the corrected source and test files only
git commit -m "test: verify permission session invalidation"
```
