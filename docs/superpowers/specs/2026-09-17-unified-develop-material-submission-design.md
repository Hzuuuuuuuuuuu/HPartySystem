# 发展党员统一材料提交与权限隔离设计

日期：2026-09-17

## 1. 背景与问题

当前发展党员 25 步流程已经具备材料模板、模板下载、材料齐备度、材料归档实体和文件基础设施，但“材料提交”链路并未完整打通：

1. `ApplicantDetail.tsx` 只能显示材料模板、下载空表/样例、查看填写说明和“已上传/待上传”状态，没有上传按钮；
2. `/file/upload` 只创建 `sys_file`，不会创建 `dev_material`，因此不能形成业务归档；
3. APPLICANT 角色当前只拥有 `develop:applicant:list/detail`，不能使用通用 `develop:applicant:handle`，而 STEP_01 / STEP_14 / STEP_22 的 `handle_roles` 又明确要求 APPLICANT 本人办理；
4. STEP_02、STEP_06、STEP_10 等步骤存在“步骤由党组织/培养联系人办理，但其中部分材料必须由申请人本人提交”的混合职责；
5. 首页对低权限用户无条件请求管理统计接口，导致 APPLICANT 登录后出现多条“没有操作权限”提示；
6. 若仅在前端按角色显示按钮，或仅复用通用文件上传接口，会留下跨用户、跨组织、跨步骤绑定材料的越权风险。

本设计建立统一、可扩展的材料提交模型，覆盖所有个人流程材料，而不是只修 STEP_01。

## 2. 已确认的材料现状

当前 `dev_material_template` 共 50 条：

- `APPLICANT`：6 条，全部必备；
- `BRANCH`：33 条，其中 25 条必备；
- `PARENT_ORG`：10 条，其中 6 条必备；
- `TRAINER`：1 条，必备。

个人流程中的典型材料包括：

- STEP_01：`1-1 入党申请书`，APPLICANT；
- STEP_02：`1-2-1 入党申请人有关事项报告表`，APPLICANT；
- STEP_06：`2-5 思想汇报`，APPLICANT；`2-6 培养考察登记表`，TRAINER；
- STEP_10：`3-6 自传`，APPLICANT；其余政审材料由 BRANCH/PARENT_ORG 出具；
- STEP_14：`4-3 入党志愿书`，APPLICANT；
- STEP_22：`5-2 转正申请书`，APPLICANT。

`is_roster=1` 的名册/台账属于组织级档案，不绑定某个申请人，不进入个人 25 步材料提交入口，也不计入个人材料齐备度。

## 3. 目标

本次实现必须满足：

1. 所有个人材料使用统一的上传、归档、查看、替换/删除能力；
2. 服务端基于 `submit_role + applicant + person + org + current step/stage + operator` 计算授权，不信任前端角色判断；
3. APPLICANT 可以提交本人应提交的材料，并在本人办理步骤完成“本人提交”，但仍不获得通用 `develop:applicant:handle`；
4. BRANCH、PARENT_ORG、TRAINER 只能在自己实际职责和数据范围内操作材料；
5. 文件和业务材料必须形成一致关联，不能出现“文件上传成功但业务材料没归档”的可见半成品；
6. 低权限用户首页不再产生无意义的 403 提示；
7. 不破坏现有党务人员办理流程和既有数据权限逻辑。

## 4. 核心授权模型

### 4.1 服务端是唯一授权源

前端只展示后端返回的能力，不根据 `roleKey`、`dataScope` 或步骤编码自行推断。

每个材料模板节点增加能力字段：

- `canUpload`：当前用户是否可以上传/替换该材料；
- `canDelete`：当前用户是否可以删除自己有权管理的该材料；
- `canPreview`：当前用户是否可以预览已归档材料；
- `uploadedCount`：当前模板已归档有效材料数量；
- `materialId` / `fileUrl`：单份材料场景下返回当前有效记录；
- `repeatable`：该模板是否允许多份或周期性重复提交。

步骤节点增加：

- `canSelfSubmit`：当前步骤是否允许当前用户执行“本人提交步骤”；
- `selfSubmitBlockedReason`：不能提交时的明确原因，例如“请先上传入党申请书”。

阶段节点增加 `materialTemplates`，用于承载 `step_code IS NULL AND is_roster=0` 的个人阶段材料。

前端只根据这些后端能力渲染按钮和提交入口。

### 4.2 基础数据隔离

任何材料写操作必须由服务端重新加载：

- `DevApplicant applicant`；
- `DevMaterialTemplate template`；
- 当前登录 `LoginUser`；
- 当前步骤/阶段；
- 已有材料记录。

客户端只允许提交 `applicantId + templateId + file`（删除时 `materialId`）。以下字段一律禁止客户端决定：

- `personId`；
- `orgId`；
- `stepCode`；
- `materialType`；
- `templateCode`；
- `isRequired`；
- `submitRole`；
- `fileUrl`；
- `createBy`。

这些字段全部由服务端根据申请人和模板生成，从根源上阻断“把自己的文件挂到别人名下”或“把未来步骤材料伪装成当前步骤材料”。

### 4.3 APPLICANT

允许条件全部满足时才能上传：

1. `template.submit_role = APPLICANT`；
2. 当前登录用户 `personId == applicant.personId`；
3. 数据权限通过 `DataScopeHelper.canAccessData(applicant.orgId, applicant.personId)`；
4. 模板属于当前允许提交的步骤/阶段；
5. 申请流程仍处于可操作状态。

APPLICANT 不授予 `develop:applicant:handle`。

### 4.4 BRANCH

BRANCH 材料上传者必须同时满足：

1. 模板 `submit_role = BRANCH`；
2. 对该申请人的 `orgId/personId` 数据范围校验通过；
3. 当前用户具备支部业务办理能力，至少命中支部书记、副书记、组织委员或超级管理员；
4. 当前模板处于可提交时段。

仅同组织但无支部办理职责的 SELF/APPLICANT 用户不能利用组织相同关系上传 BRANCH 材料。

### 4.5 PARENT_ORG

允许条件：

1. 模板 `submit_role = PARENT_ORG`；
2. 用户为超级管理员或党委级组织用户；
3. 数据范围允许访问目标申请人的组织；
4. 当前模板处于可提交时段。

不能仅凭 `data_scope=ALL/CURRENT_AND_CHILD` 就获得党委材料上传能力。

### 4.6 TRAINER

允许条件：

1. 模板 `submit_role = TRAINER`；
2. 当前用户有 `personId`；
3. 当前用户 `personId` 必须出现在 `applicant.trainerIds` 中；
4. 当前模板处于可提交时段。

TRAINER 是**显式业务关系授权**：它只授权“这个培养联系人 → 这个申请人 → 这类 TRAINER 材料”，不等价于获得该申请人的通用数据访问范围。实际培养联系人常常是 `SELF` 数据范围，如果再要求 `DataScopeHelper.canAccessData(applicant.orgId, applicant.personId)`，合法培养联系人会被错误拦截。因此 TRAINER 材料不使用通用人员数据范围作为第二道闸门，而使用 `trainerIds` 精确关系作为资源级授权；时间轴详情等其他数据读取仍遵守原有权限模型。

因此“培养联系人”不是角色表中的宽泛权限，而是申请人与具体人员之间的最小授权关系。

## 5. 可提交时段规则

### 5.1 步骤材料

`template.step_code` 非空时：

- 当前步骤：允许对应出具方上传；
- 已完成历史步骤：在整个发展流程仍处于进行中时，允许对应出具方**补录或纠正材料**，但仍必须通过同一套资源级授权；这与现有 `MaterialRequiredRule` “材料可后补、流程先提醒”的设计保持一致；
- 未来步骤：禁止上传；
- 流程结束后：默认只读，只有超级管理员或后续明确设计的档案补录能力可以修改，本次不开放普通用户补档。

历史步骤补录不会重新执行或回滚流程状态，只改变材料归档状态；如果替换已有单次材料，仍采用新记录成功后再淘汰旧记录的安全顺序。历史补录与替换仅在流程仍进行中时开放，流程结束后保持只读。

### 5.2 周期性步骤

STEP_06 等周期性考察允许多次提交同一类周期材料。`repeatable=true` 的判定以步骤类型和材料业务语义为准，不能用“模板编号是否相同”简单覆盖。

普通单次模板默认只保留一份当前有效材料；再次上传采用“替换”语义：新文件和新 `dev_material` 成功后，再逻辑删除旧材料记录，避免先删后传导致材料丢失。

### 5.3 阶段材料

`template.step_code IS NULL` 且 `is_roster=0` 的个人阶段材料，在时间轴对应阶段新增“阶段材料”区域。

允许在该阶段已经开始、尚未越过阶段结束时，由对应 `submit_role` 上传；阶段完成后转为只读。

### 5.4 组织台账

`is_roster=1`：

- 不出现在个人材料上传区；
- 不计个人材料齐备度；
- 保持组织级归档语义；
- 本次不新增组织台账管理页面，避免扩大范围。

## 6. 上传与归档事务

新增发展党员材料服务作为业务入口，例如 `DevMaterialService`，不让前端直接使用 `/file/upload` 完成业务材料归档。

推荐接口：

- `POST /develop/applicant/{applicantId}/materials/{templateId}`：multipart 上传并归档；
- `DELETE /develop/applicant/{applicantId}/materials/{materialId}`：删除/撤销当前有权管理的材料；
- 时间轴接口直接返回材料能力和当前材料状态，无需新增独立查询接口。

上传事务顺序：

1. 校验申请人、模板和授权；
2. 通过统一文件服务保存物理文件并得到 `sys_file`；
3. 创建 `dev_material`，字段由服务端生成；
4. 若为替换，成功写入新材料后逻辑删除旧材料；
5. 返回最新材料状态。

数据库事务可保证 `sys_file` 与 `dev_material` 元数据一致；本地磁盘文件无法参与数据库事务，因此若 `dev_material` 落库失败，服务层必须补偿删除刚写入的物理文件/`sys_file`，避免形成业务可见半成品。

删除时先做业务授权，再删除/逻辑删除 `dev_material`，并调用文件服务删除对应 `sys_file`。若存在共享文件引用，需先检查引用数；当前发展党员材料按“一材料一文件”使用，不创建共享引用。

### 6.1 文件 URL 也必须保持个人资源隔离

`sys_file.org_id` 只能表达组织归属，不能单独作为发展党员个人材料的读取边界。否则同一支部的另一个 SELF 用户只要拿到泄露的 `/file/preview/**` URL，就可能绕过 `dev_material` 的本人隔离。

因此通用文件服务增加可插拔的业务文件访问策略：

- `biz_type=dev_material` 时，由发展党员模块按 `sys_file.biz_id=applicantId` 重新加载申请实例；
- 申请人本人可读自己的材料；
- 实际培养联系人按 `trainer_ids` 精确关系可读；
- 党务管理人员必须同时具备发展党员详情/材料管理功能权限和申请人的数据范围；
- 同组织但非本人、非培养联系人、无管理权限的用户，即使拿到直接 URL 也返回 403；
- `dev_material` 背后的 `sys_file` 禁止通过通用 `DELETE /file/{fileId}` 直接删除，必须走发展党员材料删除接口，避免留下悬空 `dev_material` 引用。

未命中专用业务策略的其他文件仍使用原有组织数据范围，不扩大本次改动范围。

## 7. “本人提交步骤”模型

新增专用接口，例如：

`POST /develop/applicant/{applicantId}/self-submit`

只允许以下条件：

1. 当前步骤 `handle_roles` 包含 `APPLICANT`；
2. 当前用户是申请人本人；
3. 当前步骤属于本人提交型节点；
4. 本步骤所有 `APPLICANT + is_required=1 + is_roster=0` 材料已经齐备；
5. 原有资格/时间规则继续执行。

当前至少覆盖 STEP_01、STEP_14、STEP_22。

该接口内部复用流程状态机的推进能力，但不复用 `@SaCheckPermission("develop:applicant:handle")` 的 Controller 入口。服务层必须继续执行 `checkHandlePermission` 的业务身份校验，从而保证只能由本人推进本人节点。

前端在当前步骤显示：

- 未齐材料：上传按钮 + “材料未齐，暂不能提交”；
- 已齐材料：显示“提交本步骤”；
- 非本人办理步骤：不显示“本人提交”，即使该步骤内存在 APPLICANT 材料，也只允许上传材料。

因此 STEP_02 / STEP_06 / STEP_10 的本人材料不会让申请人越权推进由组织或培养联系人负责的步骤。

## 8. 前端交互

统一改造 `MaterialTemplateList`：

每份模板显示：

- 编号、名称、必备/选填、出具方；
- 已上传/待上传状态；
- 下载空表、下载样例、填写说明；
- 后端返回 `canUpload=true` 时显示“上传”或“替换”；
- 已归档且 `canPreview=true` 时显示“查看”；
- `canDelete=true` 时显示“删除”；
- 上传中、替换中、删除中有独立 loading，防止重复提交。

上传成功后只刷新当前 timeline，避免自行拼装状态。

阶段材料单独展示在阶段标题下方，避免伪造一个不存在的步骤。

## 9. 首页低权限请求裁剪

`DashboardPage` 不再无条件请求所有统计接口。

按 `useUserStore.can()` 判断：

- `develop:stat:list` 才请求发展党员总体统计；
- `system:person:list` / `orginfo:member:list` / `orginfo:tree` 任一满足才请求人员统计；
- `develop:applicant:list` 才请求最近发展对象；
- `/my/todo/count` 是个人接口，登录用户均可请求。

没有权限的卡片不显示错误提示，可显示 `—` 或直接隐藏管理型指标。APPLICANT 首页至少保留欢迎信息、本人流程入口/最近本人申请和个人待办，不产生批量 403。

## 10. 权限标识与 Flyway

不授予 APPLICANT 通用 `develop:applicant:handle`。

新增专用功能权限：

- `develop:material:submit`：发展党员材料管理能力，主要授予支部/党委等管理角色；
- `develop:applicant:self-submit`：申请人本人提交本人办理步骤。

V5 迁移：

- 给内置 APPLICANT 角色补 `develop:applicant:self-submit`；
- 给现有支部/党委管理角色按职责补 `develop:material:submit`；
- 不给 APPLICANT 授予通用 `develop:material:submit`，其本人材料上传由“本人 + 模板 submit_role=APPLICANT”的资源级授权直接放行；
- TRAINER 也不依赖 `develop:material:submit`，由 `trainerIds` 的精确业务关系授权放行。

材料上传/删除接口使用“已登录 + 服务端资源级授权”作为最终安全边界：管理角色通常需要 `develop:material:submit`，APPLICANT/TRAINER 则必须命中本人/培养关系例外。这样既不扩大低权限用户的通用管理权限，又能支持实际培养联系人为 SELF 数据范围的场景。

`develop:applicant:self-submit` 仍是本人推进步骤的第一层功能权限，服务端还必须再次校验“当前用户就是 applicant.personId 且当前步骤 handle_roles 包含 APPLICANT”。功能权限不能替代资源级校验。

## 11. 后端测试设计

永久集成测试至少覆盖：

1. APPLICANT 本人 STEP_01 上传 1-1 成功；
2. APPLICANT A 不能给 APPLICANT B 上传，即使同组织；
3. APPLICANT 不能上传 BRANCH/PARENT_ORG/TRAINER 材料；
4. APPLICANT 不能上传未来步骤材料；
5. STEP_01 材料未齐时 self-submit 拒绝，齐备后可推进 STEP_02；
6. STEP_02 有 APPLICANT 材料时本人可上传，但不能 self-submit 推进；
7. BRANCH 管理员可给授权范围内申请人上传 BRANCH 材料，跨组织拒绝；
8. PARENT_ORG 党委用户可上传党委材料，普通支部用户拒绝；
9. TRAINER 只有实际登记的培养联系人可上传 2-6，其他同组织党员拒绝；
10. 单次模板替换后只有一份有效材料；
11. 周期性材料允许多份历史记录；
12. 删除/替换越权被拒绝；
13. 失败事务不产生可见 `dev_material` 半成品；
14. 阶段材料遵守阶段窗口；
15. roster 模板不进入个人材料上传能力。

继续保留现有 DataScope、FilePreview、RolePermissionSeed 等回归，防止新材料入口绕过已修复的安全边界。

## 12. 前端验证

至少验证：

1. lxy 登录不再连续弹权限错误；
2. lxy STEP_01 看到“上传入党申请书”；
3. 上传后状态刷新为已上传，可预览；
4. 材料齐备后出现“提交本步骤”，提交后推进 STEP_02；
5. STEP_02 中 lxy 只可上传 1-2-1，不能操作支部材料，也不能推进 STEP_02；
6. 支部账号在对应材料上看到 BRANCH 上传按钮；
7. 无权用户看不到按钮，即使手工调用接口也被后端 403；
8. lint、TypeScript、Vite build 通过。

## 13. 非目标

本次不做：

- 组织级 roster/名册管理新页面；
- 对 50 份模板内容本身进行编辑；
- 把所有文件上传接口重构为通用 ACL 平台；
- 修改现有 25 步业务规则定义；
- 为材料引入审批流或版本审核流。

## 14. 兼容性与迁移

当前 `dev_material` 实际数据为 0，因此不存在历史材料迁移风险。

V1-V4 不修改，新增 V5+ 完成权限种子和必要的数据库约束/索引。若需要为单次模板增加唯一性约束，优先使用“业务查询 + 事务替换”而不是简单数据库唯一键，因为 STEP_06 周期性材料需要允许多条历史记录。

## 15. 完成标准

实现完成后必须同时满足：

- 权限和用户数据隔离测试全部通过；
- lxy 可以完成 STEP_01 本人材料上传和本人提交；
- 其他步骤材料按 APPLICANT / BRANCH / PARENT_ORG / TRAINER 稳定工作；
- 未来步骤、他人、跨组织、错误出具方均无法通过直接 API 绕过；
- 首页低权限用户无批量 403；
- 后端完整测试、前端 lint/build、实际运行态验证全部通过；
- 文档同步到接口清单、数据库设计、流程说明和巡检报告。
