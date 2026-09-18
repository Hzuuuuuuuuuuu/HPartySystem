# 发展党员统一材料提交 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 打通发展党员个人材料的统一上传、归档、预览、替换/删除与申请人本人提交链路，并用资源级授权隔离 APPLICANT、BRANCH、PARENT_ORG、TRAINER 的用户和组织数据。

**Architecture:** 在 `hparty-develop` 新增专用 `DevMaterialService` 作为材料业务边界；前端只提交 `applicantId + templateId + file`，其余归属字段全部由后端派生。时间轴由后端返回材料能力和步骤级 `canSelfSubmit`，前端只消费能力。V5 仅增加最小功能权限种子，不把 APPLICANT 暴露给通用 `develop:applicant:handle`。

**Tech Stack:** Spring Boot 3.2.5, Java 17, MyBatis-Plus, Sa-Token, Flyway, MockMvc/JUnit5/AssertJ, React 18, TypeScript 5.6, Ant Design 5, Zustand, Axios.

## Global Constraints

- 不修改 V1-V4 历史迁移；新增 V5。
- 项目根目录当前没有 `.git` 元数据，因此本计划的“提交”步骤替换为 AgentDock checkpoint + 测试证据，不虚构 commit/PR。
- `is_roster=1` 的组织台账不进入个人材料提交入口，也不计个人材料齐备度。
- APPLICANT 不授予 `develop:applicant:handle`。
- 客户端不得决定 `personId/orgId/stepCode/materialType/templateCode/isRequired/submitRole/fileUrl/createBy`。
- 未来步骤禁止提前上传；流程进行中允许对历史步骤按原出具方补录/纠正材料；流程结束后普通用户只读。
- TRAINER 使用 `applicant.trainerIds` 精确业务关系授权，不使用通用 SELF 人员数据范围作为第二道闸门。
- 任何完成声明前必须跑专项测试、完整 Maven 测试、前端 lint/build 和真实运行态验证。

---

### Task 1: 建立材料安全回归测试与权限种子 RED

**Files:**
- Create: `hparty-server/hparty-admin/src/test/java/com/hparty/security/DevelopMaterialSubmissionRegressionTest.java`
- Modify: `hparty-server/hparty-admin/src/test/java/com/hparty/security/RolePermissionSeedRegressionTest.java`
- Create in Task 2 GREEN: `hparty-server/hparty-admin/src/main/resources/db/migration/V5__develop_material_submission_permissions.sql`

**Interfaces:**
- Consumes: 现有 `/auth/login`、`/develop/applicant/{id}/timeline`、`sys_user/sys_role/dev_applicant/dev_material_template`。
- Produces: 期望 API：`POST /develop/applicant/{applicantId}/materials/{templateId}`、`DELETE /develop/applicant/{applicantId}/materials/{materialId}`、`POST /develop/applicant/{applicantId}/self-submit`。

- [ ] **Step 1: 写 RED 集成测试骨架**

测试类使用 `@SpringBootTest(properties={"hparty.captcha.enabled=false","hparty.job.enabled=false"})`、`@AutoConfigureMockMvc`、`@Transactional`。通过 JDBC 创建临时人员、申请流程、账号并绑定内置角色；上传使用 `MockMultipartFile`。

核心用例方法：

```java
@Test void applicantCanUploadOwnCurrentStepMaterial();
@Test void applicantCannotUploadAnotherApplicantsMaterialEvenInSameOrg();
@Test void applicantCannotUploadBranchOrFutureMaterial();
@Test void branchOperatorCanUploadBranchMaterialWithinScopeButNotAcrossOrg();
@Test void committeeOperatorCanUploadParentOrgMaterialButBranchOperatorCannot();
@Test void onlyAssignedTrainerCanUploadTrainerMaterial();
@Test void selfSubmitRequiresRequiredApplicantMaterialAndAdvancesStep01();
@Test void applicantMaterialOnOrganizationHandledStepDoesNotGrantSelfSubmit();
@Test void singleMaterialReplacementLeavesOneActiveRecord();
@Test void rosterTemplateCannotBeUploadedThroughPersonalMaterialEndpoint();
```

- [ ] **Step 2: 运行 RED**

Run:

```powershell
mvn -f hparty-server/hparty-admin/pom.xml -Dtest=DevelopMaterialSubmissionRegressionTest,RolePermissionSeedRegressionTest test
```

Expected: `DevelopMaterialSubmissionRegressionTest` 因接口不存在/权限不存在而失败；现有测试不应出现编译错误。

- [ ] **Step 3: 扩展权限种子断言**

`RolePermissionSeedRegressionTest` 增加：

```java
assertThat(hasPermission("APPLICANT", "develop:applicant:self-submit")).isTrue();
assertThat(hasPermission("APPLICANT", "develop:applicant:handle")).isFalse();
assertThat(hasPermission("APPLICANT", "develop:material:submit")).isFalse();
assertThat(hasPermission("BRANCH_SECRETARY", "develop:material:submit")).isTrue();
assertThat(hasPermission("ORG_COMMITTEE", "develop:material:submit")).isTrue();
assertThat(hasPermission("PARTY_SECRETARY", "develop:material:submit")).isTrue();
```

- [ ] **Step 4: AgentDock checkpoint**

记录 RED 的真实失败原因，确认不是测试拼写或环境错误。

---

### Task 2: V5 权限种子与材料业务服务 GREEN

**Files:**
- Create: `hparty-server/hparty-admin/src/main/resources/db/migration/V5__develop_material_submission_permissions.sql`
- Create: `hparty-server/hparty-develop/src/main/java/com/hparty/develop/service/DevMaterialService.java`
- Create: `hparty-server/hparty-develop/src/main/java/com/hparty/develop/controller/DevMaterialController.java`
- Modify: `hparty-server/hparty-system/src/main/java/com/hparty/system/service/SysFileService.java`

**Interfaces:**
- Produces: `DevMaterialService.upload(Long applicantId, Long templateId, MultipartFile file)`、`delete(Long applicantId, Long materialId)`、`buildCapability(DevApplicant applicant, DevMaterialTemplate template, List<DevMaterial> materials, LoginUser user)`。
- Produces HTTP: multipart `POST /develop/applicant/{applicantId}/materials/{templateId}`，`DELETE /develop/applicant/{applicantId}/materials/{materialId}`。

- [ ] **Step 1: 写 V5**

使用新功能菜单 ID `3017/3018`，父级 `301`，类型 `F`：

```sql
INSERT INTO sys_menu (...) SELECT 3017,301,'材料管理',..., 'F', ..., 'develop:material:submit', ...
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='develop:material:submit');

INSERT INTO sys_menu (...) SELECT 3018,301,'本人提交',..., 'F', ..., 'develop:applicant:self-submit', ...
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='develop:applicant:self-submit');
```

`develop:material:submit` 授给 `PARTY_SECRETARY/BRANCH_SECRETARY/BRANCH_DEPUTY/ORG_COMMITTEE`；`develop:applicant:self-submit` 只授给内置 `APPLICANT`。所有关联使用 `NOT EXISTS`，不做 DELETE/reset。

- [ ] **Step 2: 给 SysFileService 增加内部补偿接口**

新增一个受服务层调用的删除方法，允许同事务业务失败后按 `fileId` 清理刚上传的文件和 `sys_file`，但仍由调用方持有已创建 ID；不暴露新匿名 HTTP 接口。

目标签名：

```java
@Transactional(rollbackFor = Exception.class)
public boolean deleteOwnedFile(Long fileId) {
    return delete(fileId);
}
```

若当前 `delete` 的资源校验会影响补偿，则提取内部 `deleteEntity(SysFile entity)`，公开 HTTP 路径继续保留授权校验，补偿只操作刚由当前用户创建的 fileId。

- [ ] **Step 3: 实现 DevMaterialService 资源授权**

关键常量：

```java
private static final String SUBMIT_APPLICANT = "APPLICANT";
private static final String SUBMIT_BRANCH = "BRANCH";
private static final String SUBMIT_PARENT = "PARENT_ORG";
private static final String SUBMIT_TRAINER = "TRAINER";
```

授权函数：

```java
public MaterialAccess evaluateAccess(DevApplicant applicant,
                                     DevMaterialTemplate template,
                                     LoginUser user) { ... }
```

规则：
- roster 直接拒绝个人接口；
- APPLICANT：`user.personId == applicant.personId`；
- BRANCH：有 `develop:material:submit`，且 `isBranchLeader()`，且 `DataScopeHelper.canAccessData(...)`；
- PARENT_ORG：有 `develop:material:submit`，且 `isCommitteeLevel()`，且 `DataScopeHelper.canAccessData(...)`；
- TRAINER：`user.personId` 在 `trainerIds` 精确集合中；
- super admin 可管理非 roster 材料，但仍受未来步骤窗口限制，避免提前污染流程。

- [ ] **Step 4: 实现步骤/阶段窗口**

步骤模板：比较 `step.order` 与当前步骤 order：未来拒绝；当前和历史（流程仍 running）允许；流程结束普通用户拒绝。阶段材料：当前阶段或已开始且流程仍在该阶段范围内时允许；已越过阶段只允许历史补录到流程仍 running 的对应阶段，未来阶段拒绝。

- [ ] **Step 5: 实现上传事务**

上传入口只接收 `applicantId/templateId/file`：

```java
SysFileVO stored = fileService.upload(file, "dev_material", applicantId);
DevMaterial material = new DevMaterial();
material.setApplicantId(applicant.getApplicantId());
material.setPersonId(applicant.getPersonId());
material.setStepCode(template.getStepCode());
material.setMaterialType(template.getMaterialType());
material.setTemplateCode(template.getTemplateCode());
material.setMaterialName(template.getTemplateName());
material.setFileId(stored.getFileId());
material.setFileUrl(stored.getFileUrl());
material.setSubmitDate(LocalDate.now());
material.setIsRequired(template.getIsRequired());
materialMapper.insert(material);
```

若后续落库失败，catch 后调用文件补偿删除再抛出。单次模板在新记录成功后逻辑删除旧有效材料；STEP_06 周期性材料保留多条。

- [ ] **Step 6: 实现删除接口**

重新加载 material → applicant → template，通过同一 `evaluateAccess`；未来/越权/错误出具方拒绝。成功后删除业务材料与对应 file。不能仅凭 materialId 删除。

- [ ] **Step 7: 收紧业务文件 URL 的资源级读取边界**

在 `hparty-system` 增加可插拔 `SysFileAccessPolicy`，由 `hparty-develop` 提供 `dev_material` 策略。预览/下载/文件详情必须按 applicant 重新校验本人、培养联系人或管理人员的数据范围；同组织其他 SELF 用户拿到泄露 URL 仍需 403。`dev_material` 背后的 `sys_file` 禁止通过通用 `DELETE /file/{fileId}` 直接删除，只能通过业务材料接口删除，避免悬空引用。

对应 TDD 用例：
- 同组织另一个 APPLICANT 直接访问泄露的材料 URL → RED 后修成 403；
- 实际 TRAINER 直接预览自己有业务关系的材料仍为 200；
- 材料所有者直接调用通用文件删除 → 403，`dev_material + sys_file` 关联保持完整。

- [ ] **Step 8: 运行专项 GREEN**

Run 同 Task 1。Expected: 上传/越权/角色/roster/替换/self-submit/文件 URL 隔离相关用例通过。

---

### Task 3: 申请人本人提交状态机

**Files:**
- Modify: `hparty-server/hparty-develop/src/main/java/com/hparty/develop/service/DevFlowService.java`
- Modify: `hparty-server/hparty-develop/src/main/java/com/hparty/develop/controller/DevFlowController.java` 或 `DevApplicantController.java`
- Modify: `hparty-server/hparty-develop/src/main/java/com/hparty/develop/rule/impl/MaterialRequiredRule.java`（只在需要时收紧本人提交检查，不改变党务人员原有 WARN 语义）
- Test: `DevelopMaterialSubmissionRegressionTest.java`

**Interfaces:**
- Produces: `DevFlowService.selfSubmit(Long applicantId)`。
- Produces: `POST /develop/applicant/{applicantId}/self-submit`，要求 `develop:applicant:self-submit`。

- [ ] **Step 1: 确认 RED 精确失败**

只跑：

```powershell
mvn -f hparty-server/hparty-admin/pom.xml -Dtest=DevelopMaterialSubmissionRegressionTest#selfSubmitRequiresRequiredApplicantMaterialAndAdvancesStep01 test
```

Expected: 404/无对应方法，而非数据库准备失败。

- [ ] **Step 2: 实现 selfSubmit**

服务层必须：

```java
DevApplicant applicant = loadApplicant(applicantId);
LoginUser operator = SecurityUtils.getLoginUser();
DevStep step = stepService.getByCode(applicant.getCurrentStep());

BizException.throwForbiddenIf(operator.getPersonId() == null
        || !operator.getPersonId().equals(applicant.getPersonId()), "只能提交本人的发展流程");
BizException.throwForbiddenIf(!containsHandleToken(step, "APPLICANT"), "当前步骤不是本人办理步骤");
```

再查询当前步骤所有 `submit_role=APPLICANT AND is_required=1 AND is_roster=0` 模板，逐个按 `template_code` 判断已归档；缺失时返回明确错误。齐备后构造 `DevHandleDTO(result=1, applicantId=...)`，复用状态机内部推进逻辑，而不是调用受 `develop:applicant:handle` Controller 保护的 HTTP 入口。

- [ ] **Step 3: 保持组织办理材料规则兼容**

普通 `handle()` 仍使用 `MaterialRequiredRule` WARN 语义，不因为本次 self-submit 的“必备材料必须齐”而全局变成硬阻断。

- [ ] **Step 4: 运行两个 self-submit 用例 GREEN**

Expected:
- STEP_01 无 1-1 → 业务拒绝；
- 上传 1-1 → self-submit 推进 STEP_02；
- STEP_02 即使本人上传 1-2-1，self-submit 仍 403/业务拒绝，因为 `handle_roles` 不含 APPLICANT。

---

### Task 4: 时间轴能力模型与阶段材料

**Files:**
- Modify: `hparty-server/hparty-develop/src/main/java/com/hparty/develop/domain/vo/DevTimelineVO.java`
- Modify: `hparty-server/hparty-develop/src/main/java/com/hparty/develop/service/DevApplicantService.java`
- Modify: `hparty-server/hparty-develop/src/main/java/com/hparty/develop/service/DevMaterialTemplateService.java`
- Test: `DevelopMaterialSubmissionRegressionTest.java`

**Interfaces:**
- `MaterialTemplateNode`: `canUpload/canDelete/canPreview/uploadedCount/materialId/fileUrl/repeatable`。
- `StepNode`: `canSelfSubmit/selfSubmitBlockedReason`。
- `StageNode`: `materialTemplates`。

- [ ] **Step 1: 写 capability RED 断言**

对 lxy/APPLICANT timeline 断言 STEP_01 的 1-1：`canUpload=true`；BRANCH 模板 `canUpload=false`；未来 STEP_14 `canUpload=false`；STEP_01 `canSelfSubmit` 随材料齐备变化。

- [ ] **Step 2: 扩展 VO**

Java 字段与 TS 命名一一对应，不使用动态 Map 表达 capability。

- [ ] **Step 3: 由 DevMaterialService 统一计算 capability**

`DevApplicantService` 不复制权限判断；注入 `DevMaterialService`，对每个模板调用 `buildCapability(...)`。材料状态优先按 `templateCode` 精确聚合。

- [ ] **Step 4: 加阶段材料**

`template.stepCode == null && isRoster != 1` 按 stageCode 分组挂到 `StageNode.materialTemplates`；齐备度也纳入这些“个人阶段必备材料”，但 roster 继续排除。

- [ ] **Step 5: GREEN**

跑专项回归并确认 timeline JSON 能力字段符合不同账号身份。

---

### Task 5: 前端统一材料交互与本人提交

**Files:**
- Modify: `hparty-web/src/api/develop.ts`
- Modify: `hparty-web/src/pages/develop/ApplicantDetail.tsx`
- Optional create: `hparty-web/src/pages/develop/MaterialTemplateList.tsx`（若 ApplicantDetail 继续膨胀则抽离；只做本功能相关拆分）

**Interfaces:**
- API: `uploadApplicantMaterial(applicantId, templateId, file)`、`deleteApplicantMaterial(applicantId, materialId)`、`selfSubmitApplicantStep(applicantId)`。
- UI 只消费后端 capability。

- [ ] **Step 1: 扩展 TypeScript 类型**

```ts
export interface DevMaterialTemplateNode {
  ...
  canUpload?: boolean;
  canDelete?: boolean;
  canPreview?: boolean;
  uploadedCount?: number;
  materialId?: number;
  fileUrl?: string;
  repeatable?: boolean;
}

export interface DevStepNode {
  ...
  canSelfSubmit?: boolean;
  selfSubmitBlockedReason?: string;
}
```

`DevStageNode` 增加 `materialTemplates?: DevMaterialTemplateNode[]`。

- [ ] **Step 2: 新增 API**

```ts
export const uploadApplicantMaterial = (applicantId: number, templateId: number, file: File) => {
  const form = new FormData();
  form.append('file', file);
  return http.upload(`/develop/applicant/${applicantId}/materials/${templateId}`, form);
};

export const deleteApplicantMaterial = (applicantId: number, materialId: number) =>
  http.delete<void>(`/develop/applicant/${applicantId}/materials/${materialId}`);

export const selfSubmitApplicantStep = (applicantId: number) =>
  http.post<DevHandleResult>(`/develop/applicant/${applicantId}/self-submit`);
```

- [ ] **Step 3: MaterialTemplateList 支持上传/替换/查看/删除**

用 AntD `Upload` 的 `beforeUpload` 自定义上传，始终 `return false`/阻止默认 HTTP。上传成功后 `fetchTimeline()`；按钮严格根据 `canUpload/canDelete/canPreview`。

- [ ] **Step 4: 当前步骤显示本人提交按钮**

`canSelfSubmit=true` 时显示主按钮；false 且有 `selfSubmitBlockedReason` 时显示禁用按钮/提示。成功后刷新 timeline。

- [ ] **Step 5: 渲染阶段材料**

阶段标题下显示 `stage.materialTemplates`，复用同一 MaterialTemplateList，不复制上传逻辑。

- [ ] **Step 6: 静态验证**

Run:

```powershell
npm run lint
npm run build
```

Expected: TypeScript 与 Vite build 均成功。

---

### Task 6: 首页低权限请求裁剪

**Files:**
- Modify: `hparty-web/src/pages/dashboard/index.tsx`

**Interfaces:**
- Consumes: `useUserStore().can`。
- Produces: 只有拥有对应权限时才发管理统计请求。

- [ ] **Step 1: 按权限构造 Promise**

```ts
const can = useUserStore((s) => s.can);
const canDevStat = can('develop:stat:list');
const canApplicantList = can('develop:applicant:list');
const canPersonStat = can('system:person:list') || can('orginfo:member:list') || can('orginfo:tree');
```

只对 true 的权限调用 API；`getMyTodoCount()` 始终调用。

- [ ] **Step 2: 管理型卡片无权限时隐藏或显示 —**

不能通过“发请求后 catch 403”控制 UI。APPLICANT 页面不应产生 `develop:stat:list` 和 `/system/person/statistics` 请求。

- [ ] **Step 3: lint/build**

再次运行前端验证。

---

### Task 7: 文档、全量回归和真实 lxy 验证

**Files:**
- Modify: `docs/02-入党流程25步定义.md`
- Modify: `docs/03-数据库设计.md`
- Modify: `docs/04-接口清单.md`
- Modify: `docs/08-全系统回归巡检报告.md`

**Interfaces:** N/A。

- [ ] **Step 1: 完整后端测试**

```powershell
mvn -f hparty-server/pom.xml test
mvn -f hparty-server/pom.xml clean install -DskipTests
```

Expected: BUILD SUCCESS，新增专项测试与原有 86 项均无回归。

- [ ] **Step 2: 前端最终验证**

```powershell
npm run lint
npm run build
```

- [ ] **Step 3: 重启最新 fat-jar**

停止旧 8080，使用 `hparty-admin/target/hparty-admin.jar` 启动 dev profile；确认 8080/5173 均监听，验证码与 Vite proxy HTTP 200。

- [ ] **Step 4: 真实 lxy 验证**

在不修改 lxy 真实流程数据的前提下，优先使用事务集成测试完成“实际推进”验证；运行态只登录 lxy 获取 `userInfo/timeline`，验证：
- perms 有 `develop:applicant:self-submit`，没有 `develop:applicant:handle`；
- STEP_01 1-1 返回 `canUpload=true`；
- BRANCH/未来模板返回 false；
- 首页相关请求不再触发无权限接口。

若需要手工验证上传/推进，创建临时 applicant/user 数据并在验证后清理，不改变 lxy 的真实业务进度。

- [ ] **Step 5: 数据残留检查**

检查 `scope_* / preview_* / material_test_*` 临时用户、`security_test` 文件、临时 `dev_material` 均为 0；Flyway 版本应为 V5；`sys_role_menu` 无重复。

- [ ] **Step 6: AgentDock final review**

所有完成条件逐项给出实际测试/HTTP/数据库证据，再 complete 任务。
