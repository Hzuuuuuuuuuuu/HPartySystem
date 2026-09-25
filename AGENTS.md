# AGENTS.md — 智慧党建管理系统

> 本文件是接手本项目的 AI 编码代理的**入口**。请先读完本文，再按「文档索引」深入。
> 最后更新：2026-09-17

## 开始开发前

人类和 AI 都必须遵守 [`CONTRIBUTING.md`](CONTRIBUTING.md)：先从最新 `dev` 新建工作分支，
按下方文档索引核对要求；提交 PR 前在本地合并最新 `origin/dev`、解决全部冲突并验证，
PR 目标为 `dev`，由管理员审核合并。不得直接向 `main` 或 `dev` 推送功能修改。
AI 代理开始修改前先检查当前分支和工作区状态，不覆盖其他人或代理的未提交改动；
无法完成验证时在 PR 中如实说明，不能宣称已通过。

## 这是什么

面向基层党组织的党务管理系统，Java + React 前后端分离。覆盖三会一课、主题党日、发展党员、
组织生活会、党费、党组织基本情况、换届、党员教育、党纪学习、党员服务、先优评选 11 个业务模块，
外加组织关系转接、我的待办、民主评议党员、统计报表导出、年度计划与指标 5 个扩展模块，以及系统管理。

**核心是发展党员全流程**：依据中央组织部《中国共产党发展党员工作流程图》实现的 5 阶段 25 步状态机，
并配套《广西发展党员工作手册》要求的 50 份表格模板（按编号映射到具体步骤）。

**当前目标：从演示可用走向生产上线。** 见 `docs/06-生产上线清单.md`。

## 技术栈

| 层 | 选型 | 版本 |
|---|---|---|
| 后端 | Spring Boot + JDK 17 | 3.2.5 |
| ORM | MyBatis-Plus | 3.5.7 |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis | 7.x |
| 鉴权 | Sa-Token | 1.38.0 |
| 接口文档 | Knife4j (springdoc-openapi 3) | 4.5.0 |
| 前端 | React + TypeScript + Vite | 18 / 5.x |
| UI | Ant Design | 5.x |
| 前端状态/请求 | Zustand + TanStack Query + Axios | |

## 目录结构

```
HPartySystem/
├── AGENTS.md              本文件
├── README.md              快速开始、演示账号
├── hparty-server/         后端（Maven 多模块）
│   ├── hparty-common/     统一返回体 R<T>、异常、枚举、常量、BaseEntity
│   ├── hparty-framework/  Sa-Token 配置、数据权限、全局异常、文件存储、操作日志切面
│   ├── hparty-system/     用户/角色/菜单/党组织/字典/日志/文件/人员档案
│   ├── hparty-party/      三会一课、组织生活、党费、换届、教育、党纪、服务、评选
│   ├── hparty-develop/    发展党员 5 阶段 25 步流程引擎 + 8 个规则策略
│   └── hparty-admin/      启动模块 + application.yml
├── hparty-web/            前端（React + TS + Vite）
├── sql/                   01-建表 / 02-系统初始化 / 03-演示数据
├── scripts/               verify-fixes.sh 缺陷回归验证脚本
└── docs/                  设计文档（见下）
```

两处值得先知道的位置：

- **发展党员流程引擎的单元测试**：
  `hparty-server/hparty-develop/src/test/java/com/hparty/develop/rule/`（8 条规则 / 67 个用例）
- **材料模板文件**：`hparty-server/hparty-admin/src/main/resources/material-templates/`
  （`blank/` 50 个空表、`sample/` 14 个填写样例，随 jar 打包）

## 文档索引

| 文档 | 什么时候读 |
|---|---|
| `CONTRIBUTING.md` | **任何开发前必读**。分支、同步冲突、验证、PR 与管理员审核流程 |
| `docs/01-系统设计.md` | **先读这份**。架构、权限模型、流程引擎设计思路、模块清单 |
| `docs/02-入党流程25步定义.md` | 改发展党员相关代码前必读。25 步逐条定义（办理角色/期限/材料/规则） |
| `docs/03-数据库设计.md` | 建表、加字段、写 SQL 前必读。49 张表的分组、关联、**贯穿全局的设计约定** |
| `docs/04-接口清单.md` | 写前端调用或新增接口前查。全部 REST 接口的路径/方法/参数位置/权限 |
| `docs/05-开发规范.md` | **写任何代码前必读**。分层约定、命名、返回体、数据权限用法 |
| `docs/06-生产上线清单.md` | 上线前。必须处理的安全与运维事项 |
| `docs/07-扩展模块说明.md` | 改动扩展模块（转接/待办/评议/导出/计划）前必读。含**有意为之的口径取舍** |

## 硬性约定（违反了一定会出问题）

这些是踩过坑总结出来的，不是风格偏好：

1. **模块依赖严格单向**：`admin → develop/party/system → framework → common`。禁止反向依赖。
   `hparty-party` 不依赖 `hparty-system`，跨域取数据用注解 SQL（见 `PartyLookupMapper`）。

2. **实体是否继承 `BaseEntity` 必须逐表核对 DDL**。`BaseEntity` 含 5 个审计字段
   （create_by/create_time/update_by/update_time/del_flag），表中缺任意一列就不能继承，
   要改为 `implements Serializable` 并自行声明实际拥有的列。**别照抄别的实体。**

3. **逻辑删除 + 唯一约束必须用生成列**。直接建在业务列上会让墓碑行占用索引，
   导致"删除后无法重建同名"。见 `docs/03-数据库设计.md` 第 2.2 节。

4. **列表查询必须应用数据权限**：`DataScopeHelper.apply(wrapper)`；
   **按主键的详情/改/删必须越权校验**：`DataScopeHelper.canAccessOrg(orgId)`。
   只做前者拦不住直接构造 ID 的请求。

5. **接口的 `component` 字段（菜单表）必须是纯组件路径**，不能带查询串 —— 动态路由按
   组件文件名解析，带了就找不到文件、路由不注册、菜单变死链。筛选项由页面**从 URL 路径推导**。

6. **实体改动后要核对完整 Flyway 迁移链并同步 `docs/03-数据库设计.md`。**
   `sql/01-schema.sql` 只记录初始结构，不能代替后续迁移。

## 当前状态

### 已完成并验证

**16 个业务模块** + 系统管理全部实现：最初的 11 个（三会一课、主题党日、发展党员、
组织生活会、党费、党组织基本情况、换届、党员教育、党纪学习、党员服务、先优评选）
加上后来追加的 5 个（**组织关系转接、我的待办、民主评议党员、统计报表导出、年度计划与指标**）。

数据库 **49 张表**，后端 **26 个 Controller / 190 个接口**，前端 **34 个页面**。
后端全量构建通过，前端 `npm run build` 通过、`npx tsc --noEmit` 零错误。

### 测试覆盖

**发展党员流程引擎有单元测试**：`hparty-develop/src/test/java/com/hparty/develop/rule/` 下
**8 条规则、67 个用例**。时间通过 `DevContext.now` 注入固定值（`Fixtures.NOW`），
断言不随运行日期漂移。**改动任何规则后必须跑**：

```bash
mvn -f hparty-server/pom.xml -pl hparty-develop -am test
```

用例重点锁定的是**曾经出过 bug 的行为** —— 双过半的分母是「应到」、延长预备期后必须继续受约束、
周期性步骤的起算点取首次而非最后一次、超期只 WARN 不 REJECT。测试变红时先确认
是不是把有意为之的设计改掉了，见 `docs/01-系统设计.md` 5.4 节。

**接口层有集成回归测试**：`hparty-server/hparty-admin/src/test/java/com/hparty/` 下用
`@SpringBootTest` + MockMvc 打通「登录 → 鉴权 → Service → 落库」整条链路，当前覆盖
超管管理节点、数据权限越权、文件预览、发展党员材料提交、活动任务 CRUD 等场景。
**改这几个模块后必须跑全量**：

```bash
mvn -f hparty-server/pom.xml test
```

给这批用例**新增**属性前先读「踩过的坑」里 `OrgPathResolver` 那条：多一组属性就可能
让别的用例变红。公共覆盖项统一写在 `hparty-admin/src/test/resources/config/application.yml`。

其余保障手段：
- `scripts/verify-fixes.sh` —— 16 项缺陷回归检查（会改数据，跑完需用 `sql/03-init-demo.sql` 复位）
- 接口文档 `http://localhost:8080/api/doc.html`（Knife4j，可在线调试）

**仍未覆盖**：`DevFlowService` 的推进/驳回/终止三条流转分支（只有集成层面验证过）、
以及**前端全部** —— 前端没有自动化用例，只保证 `npx tsc --noEmit` 与 `npm run build` 通过。
Service / Controller 层**尚未逐接口覆盖**，上面那批集成回归只覆盖了它点名的场景。

### 已具备的生产化能力

| 能力 | 实现 |
|---|---|
| **数据库版本管理** | Flyway，`db/migration/` 下 V1 建表、V2 系统数据及后续增量迁移 |
| **密码策略** | `PasswordPolicy`：强度校验 + 首次登录强制改密 + 90 天过期；`POST /auth/changePassword` 自助改密 |
| **定时任务** | `com.hparty.job.HPartyScheduledTasks`：党费账单生成、超期扫描、日志清理，均幂等 |
| **生产配置** | `application-prod.yml`：SQL 日志关闭、Knife4j 关闭、日志落盘切割 |
| **越权防护** | 列表用 `DataScopeHelper.apply`，按主键的操作用 `canAccessOrg` |
| **操作审计** | `@OperLog` 注解 + 切面，成功与失败都留痕 |

### 已知待修缺陷

以下均为 **2026-09-25 记录、未修复**，全部集中在发展党员模块。动这个模块前先读对应章节，
注意其中「已核实」是静态复核结论，**不等于已在运行态复现**，修复前需先补复现信息。

- **权限等级与实际可做操作不符** —— 用户报告普通成员可进行「同意申请」一类越级操作。
  静态核实定位到 `DevFlowService.checkHandlePermission` 的 fail-open 分支等**两处**
  结构性风险点；但按种子数据推演，普通党员本不该有该权限，差异待查。
  见 [`docs/08-全系统回归巡检报告.md`](docs/08-全系统回归巡检报告.md) 第 16 节。
- **新增发展对象缺少人员身份校验与跨组织约束** —— 选人列表看不出对方是否已是党员、
  可越级添加、且已是党员也能被纳入发展流程。前一条改前端即可，后两条**必须加后端校验**。
  见第 17 节。

### 尚未实现

见 `docs/07-扩展模块说明.md` 第六节。主要是：民主评议与先优评选的联动、党员积分/党建考核、
群众评议的独立入口。功能之外的待办见 `docs/06-生产上线清单.md`。

## 怎么跑起来

```bash
# 1. 只建一个空库即可 —— 表结构与系统数据由 Flyway 在应用启动时自动应用
mysql -u root -p -e "CREATE DATABASE hparty DEFAULT CHARACTER SET utf8mb4;"

# 2. 后端（端口 8080，context-path 为 /api）
cd hparty-server
mvn clean install -DskipTests
java -jar hparty-admin/target/hparty-admin.jar
# 启动日志里会看到 Flyway 应用 V1(建表 49 张) + V2(系统数据: 菜单/角色/字典/流程模板/管理员)
# 接口文档 http://localhost:8080/api/doc.html

# 3. 演示数据（可选，**仅开发环境** —— 它不是迁移，需手工执行）
mysql -u root -p --default-character-set=utf8mb4 -e "source sql/03-init-demo.sql"

# 4. 前端（端口 5173，已配好把 /api 代理到 8080）
cd hparty-web
npm install && npm run dev
```

数据库连接配置在 `hparty-server/hparty-admin/src/main/resources/application.yml`，
可用环境变量 `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` / `REDIS_HOST` 覆盖。

### 结构变更流程（重要）

**不要再手工在库上执行 ALTER。** 数据库由 Flyway 管理，迁移脚本在
`hparty-admin/src/main/resources/db/migration/`：

| 文件 | 内容 |
|---|---|
| `V1__init_schema.sql` | 49 张表的建表语句 |
| `V2__init_system_data.sql` | 菜单、角色、字典、发展党员阶段/步骤模板、材料模板、管理员账号 |
| `V3__repair_party_secretary_permissions.sql` | 修复党委书记权限种子数据 |
| `V4__grant_applicant_detail_permission.sql` | 补齐发展对象详情权限 |
| `V5__develop_material_submission_permissions.sql` | 补齐发展材料提交权限 |

**加表 / 加列 / 改索引的步骤**：

1. 查看已有迁移的最大版本号，在 `db/migration/` 下新增下一个版本的 `V<版本号>__描述.sql`（写清用途）
2. 启动应用，Flyway 自动应用；`flyway_schema_history` 表可查执行记录
3. 同步更新 `docs/03-数据库设计.md`，并核对实体与 DDL

两条约束：
- **已应用过的迁移文件不能再改** —— `validate-on-migrate: true` 会校验文件校验和，
  改了会导致启动失败。要修正只能新增一个迁移。
- `sql/01-schema.sql` 与 `sql/02-init-system.sql` 现在只是**可读的参考副本**，
  对应 V1/V2 的初始内容，不参与执行。后续结构与系统数据变化以完整迁移链为准，
  不要为新变更改写这两个参考副本。

对于「已有数据但没有迁移历史」的老库（比如从更早版本升级上来的），
`baseline-on-migrate: true` + `baseline-version: 2` 会自动打基线，
**不会重跑 V1/V2**（V1 含 `DROP TABLE`，重跑会清空数据）。

## 演示账号（密码统一 123456）

| 账号 | 角色 | 用途 |
|---|---|---|
| `admin` | 超级管理员 | 全部权限。归属组织是 V6 迁移建的**管理节点**（`org_type=9`），因此各模块的新增都不再受「无归属组织」影响 |
| `zgq` | 郑州市党委书记 | **走完整 25 步必须用它** —— 有 6 步由「上级党委」办理 |
| `zsf` / `zw` | 第一/第二支部书记 | |
| `liming` | 组织委员 | 发展党员主要办理人 |
| `zhaoxue` | 普通党员 | 验证「仅本人数据」权限范围 |
| `lxy` | 入党申请人 | 验证当事人视角 |

## 踩过的坑（省你时间）

- **`characterEncoding` 在 JDBC URL 里要填 Java 字符集名 `UTF-8`**，填 MySQL 的 `utf8mb4`
  会直接连不上库。
- **`LambdaQueryWrapper` 与 `QueryWrapper` 是兄弟类**，不是父子。工具方法若两者都要兼容，
  参数类型得写 `AbstractWrapper`。见 `DataScopeHelper`。
- **作为 query 参数传递的 `LocalDateTime` 必须用 ISO 格式**（`2026-01-01T00:00:00`），
  空格分隔会 400 —— 项目没配 `@DateTimeFormat`，也没配 `spring.mvc.format.date-time`。
- **`DevFlowService.handle()` 里 `applicantMapper.updateById()` 必须排在 `syncPerson()` 之后**，
  否则 `syncPerson` 写入的日期字段只停留在内存对象上，永远不会落库。
  这个顺序错了会导致流程在第 13 步死锁，且不会报错。
- **支部大会「双过半」的两个半数，分母都是「应到」人数**，不是实到。
  写成实到的话，到场人数越少反而越容易通过。见 `VoteRule`。
- **`@OperLog` 注解可给 Controller 方法加操作审计**，成功与失败都会记录。

- **列表批量补名称时，空结果别用 `Map.of()` 兜底。** 不可变空 Map 的 `get(null)` 会抛
  `NullPointerException`（`Collections.emptyMap().get(null)` 才是返回 null），普通 `HashMap`
  则允许 null 键。只有**整页记录都缺该字段**时才复现：`sys_user.org_id` 可为 NULL
  （超管原先没有归属组织，V6 起挂到管理节点，但该列依旧可空），历史上表现为
  「单独搜 admin 报空指针」。调用点显式判空即可，
  详见 `docs/05-开发规范.md` 4.4 节。

- **Windows 下必须先停后端再构建，否则会得到一个残缺的 jar。**
  后端在跑时文件被锁，`mvn clean install` 的 `repackage` 步骤会报
  `Unable to rename ...jar to ...jar.original`，**但 jar 文件仍然存在** ——
  只是被留成了 600 KB 左右的「瘦 jar」（只有自己的 class，没有 `BOOT-INF/lib` 里的依赖）。
  它看起来像构建成功了，实际上启动会因缺依赖而失败，或者起在旧代码上。
  ```bash
  # 正确顺序
  Get-Process java | Where-Object { $_.Path -like "*LibericaJDK-17*" } | Stop-Process -Force
  mvn -f hparty-server/pom.xml clean install -DskipTests
  # 然后才启动
  ```
  判断 jar 是否完整：正常约 **75–80 MB**，瘦 jar 约 **600 KB**。

- **后端成功启动不代表加载的是最新代码。** 多人（或多代理）并行开发时，
  别人可能用旧 jar 重启了服务。确认方式：比对 jar 的修改时间与 JVM 启动时间，
  或者直接测行为 —— 这一点在本项目的历史上真实造成过一次误导
  （审计时运行的 jar 不含已修复的代码，结论一度对不上）。

- **UPDATE 语句里不要用 `<>`（`.ne()`），也不要用 `OR`，尤其是带逻辑删除的表。**
  `MybatisPlusConfig` 里配了 `BlockAttackInnerInterceptor`（防误写全表更新），
  它的判定比名字严格得多 —— 会对两类**完全合法**的语句抛
  `Prohibition of table update operation`：

  1. UPDATE 的 WHERE 里出现 **`OR`**
  2. UPDATE 的 WHERE 里出现 **`<>`**，**且该表有 `del_flag`**

  第 2 条最阴险：它的内部 `fullMatch` 把 `列 <> ?` 误认为「恒真条件」，
  与 MP 自动追加的 `del_flag = ?` 一相与就判定为「无有效 WHERE」。
  **同一句写法在没有 `del_flag` 的表上完全正常**，所以很难联想到原因 ——
  本项目的党费欠缴刷新就因此失败过，排查花了很久。

  **规避写法**：把条件判断挪到 Java 侧，UPDATE 只用 `IN`：
  ```java
  // 不行：WHERE status <> ? 会被拦
  // 可以：先 SELECT 出主键，再 UPDATE ... IN (ids)
  List<Long> ids = mapper.selectList(new LambdaQueryWrapper<T>()
          .select(T::getId).ne(T::getStatus, X)).stream().map(T::getId).toList();
  if (!ids.isEmpty()) {
      mapper.update(null, new LambdaUpdateWrapper<T>().set(T::getFlag, 0).in(T::getId, ids));
  }
  ```
  SELECT **不受影响**（拦截器只管 UPDATE/DELETE），所以 OR 和 `<>` 在查询里随便用。
  等价替换也行：`is_overdue <> 1` → `is_overdue = 0`（该列是 NOT NULL DEFAULT 0）。

  **注意**：不要为了图省事把拦截器摘掉 —— 它能挡住真正的全表更新，
  在党费、党员这类数据上误写一次后果严重。

- **`OrgPathResolver` 把构造注入的 `DataScopeMapper` 存进了 `static` 字段，测试里会被
  「最后创建的 ApplicationContext」抢走。** 生产只有一个上下文所以看不出问题，但集成测试
  每多一组**不同的** `@SpringBootTest(properties = ...)` 就会多建一个上下文连带一个连接池，
  后建的把静态字段覆盖掉，先建的上下文随后就拿到**别人的**连接池 —— 表现为查不到本事务里
  尚未提交的数据（例如刚插入的 `sys_role_dept` 行），不相干的越权用例莫名 403。
  实测：只加一个带独立属性的新用例类，就能让 `DataScopeEndpointRegressionTest` 变红。

  **规避**：集成测试的属性集合尽量统一，公共覆盖项放
  `hparty-admin/src/test/resources/config/application.yml`。注意放
  `src/test/resources/application.properties` 是**压不住** main 的 `application.yml` 的
  （同目录 `.yml` 优先级更高，实测无效），必须放到 `config/` 子目录下才生效。
  **根治办法**是把该静态字段改成普通依赖注入 —— 属独立改造，需同步改所有调用点。
