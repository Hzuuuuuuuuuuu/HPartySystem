# 智慧党建管理系统

面向基层党组织的党务管理系统，覆盖三会一课、发展党员、组织生活会、党费管理、党组织换届等 11 个业务模块。

核心是**发展党员全流程**：依据《中国共产党发展党员工作流程图》（中央组织部组织一局编），实现 5 个阶段、25 个步骤的状态机，含强时间约束（培养教育满 1 年、上级党委审批 3 个月内）、支部大会双过半票数规则、周期性考察与三分支出口。

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Spring Boot 3.2 · JDK 17 · MyBatis-Plus 3.5 · MySQL 8 · Redis |
| 鉴权 | Sa-Token 1.38（注解式鉴权、多端登录、踢人下线） |
| 接口文档 | Knife4j (springdoc-openapi 3) |
| 前端 | React 18 · TypeScript · Vite 5 · Ant Design 5 |
| 状态/请求 | Zustand · TanStack Query · Axios |
| 报表 | EasyExcel |

## 目录结构

```
HPartySystem/
├── hparty-server/                后端（Maven 多模块）
│   ├── hparty-common/            统一返回体、异常、枚举、常量、基础实体
│   ├── hparty-framework/         Sa-Token 配置、数据权限、全局异常、文件存储、操作日志切面
│   ├── hparty-system/            用户/角色/菜单/党组织/字典/日志/文件/人员档案
│   ├── hparty-party/             三会一课、主题党日、组织生活会、党费、换届、教育、党纪、服务、先优评选
│   ├── hparty-develop/           发展党员 5 阶段 25 步流程引擎 + 规则策略
│   └── hparty-admin/             启动模块
├── hparty-web/                   前端（React + TS + Vite）
├── sql/
│   ├── 01-schema.sql             建表（43 张）
│   ├── 02-init-system.sql        菜单、角色、字典、阶段与步骤模板、管理员
│   └── 03-init-demo.sql          演示数据
├── scripts/
│   └── verify-fixes.sh           缺陷回归验证脚本
└── docs/                         详见下方文档索引
```

## 文档索引

**接手的开发者（或 AI 编码代理）请从 [`AGENTS.md`](AGENTS.md) 开始。**

| 文档 | 内容 |
|---|---|
| [`AGENTS.md`](AGENTS.md) | **入口**。项目概览、硬性约定、当前状态、踩过的坑 |
| [`docs/01-系统设计.md`](docs/01-系统设计.md) | 架构、技术选型、权限模型、流程引擎设计思路 |
| [`docs/02-入党流程25步定义.md`](docs/02-入党流程25步定义.md) | 25 步逐条定义（办理角色/期限/材料/规则） |
| [`docs/03-数据库设计.md`](docs/03-数据库设计.md) | 49 张表的分组、关联、贯穿全局的设计约定 |
| [`docs/04-接口清单.md`](docs/04-接口清单.md) | 全部 REST 接口的路径/方法/参数位置/权限 |
| [`docs/05-开发规范.md`](docs/05-开发规范.md) | 分层约定、命名、返回体、数据权限用法 |
| [`docs/06-生产上线清单.md`](docs/06-生产上线清单.md) | **上线前必办事项**（安全、配置、运维） |
| [`docs/07-扩展模块说明.md`](docs/07-扩展模块说明.md) | 扩展模块（转接/待办/评议/导出/计划）的说明与设计取舍 |

## 快速开始

### 1. 建库

只需建一个**空库**，表结构与系统数据由 Flyway 在后端启动时自动应用：

```bash
mysql -u root -p -e "CREATE DATABASE hparty DEFAULT CHARACTER SET utf8mb4;"
```

需要 MySQL 8 与 Redis 已启动。数据库连接配置在 `hparty-server/hparty-admin/src/main/resources/application.yml`，默认 `root/123456`，可用环境变量 `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` / `REDIS_HOST` 覆盖。

> **结构变更请新增迁移脚本**，不要手工 `ALTER`。迁移文件在
> `hparty-server/hparty-admin/src/main/resources/db/migration/`（当前 V1 建表、V2 系统数据），
> 新增 `V3__xxx.sql` 后启动即自动应用。详见 `AGENTS.md` 的「结构变更流程」。

### 2. 启动后端

```bash
cd hparty-server
mvn clean install -DskipTests
java -jar hparty-admin/target/hparty-admin.jar
```

端口 8080，context-path 为 `/api`。接口文档：<http://localhost:8080/api/doc.html>

### 3. 启动前端

```bash
cd hparty-web
npm install
npm run dev
```

访问 <http://localhost:5173>。Vite 已配置把 `/api` 代理到 8080。

## 演示账号

密码统一 `123456`。

| 账号 | 角色 | 说明 |
|---|---|---|
| `admin` | 超级管理员 | 全部权限 |
| `zgq` | 郑州市党委书记 | **办理「上级党委审批/备案」类步骤必须用它** |
| `zsf` | 第一支部书记 | |
| `liming` | 第一支部组织委员 | 发展党员主要办理人 |
| `zw` | 第二支部书记 | |
| `zhaoxue` | 普通党员 | 验证「仅本人数据」权限范围 |
| `lxy` | 入党申请人 | 验证当事人视角 |

> 25 步里有 6 步（04 报上级党委备案、08 报上级党委备案、13 上级党委预审、17 上级党委审批、18 再上一级备案、24 上级党委审批）由**上级党委**办理。只用支部账号会卡在阶段二。

## 核心设计：发展党员流程引擎

不用 Flowable/Activiti 等 BPMN 引擎，而是**自定义状态机 + 规则策略**。理由：25 个步骤固定不变，BPMN 的动态编排能力用不上；而党务规则（1 年培养期、双过半票数）是领域规则，用 BPMN 表达需要大量监听器硬编码。

```
dev_stage(5条)  →  dev_step(25条)  →  dev_step_record(动态记录)
                                    dev_applicant(一人一条：当前阶段/步骤/进度)
```

步骤分两类：

- **单次办理型**（23 步）：办理一次即流转
- **周期性考察型**（STEP_06 培养教育考察每半年一次、STEP_21 继续教育考察）：在同一步骤下落多行记录，需显式提交 `advance=true` 才推进到下一步

规则与步骤的绑定关系存在 `dev_step.rule_key` 字段里，由 `DevRuleEngine` 按序执行：

| 规则 | 校验内容 | 适用步骤 |
|---|---|---|
| `QUALIFICATION_RULE` | 年龄、培养联系人/入党介绍人数量与资格 | 01、05、09、13 |
| `DEADLINE_RULE` | 办结时限，**超期只警告不阻断** | 02(30天)、17/24(90天) |
| `INTERVAL_RULE` | 距基准步骤须满 N 天，**硬性阻断** | 06、07、21、22 |
| `PERIODIC_RULE` | 周期性步骤两次记录的最小间隔 | 06、21 |
| `TRAINING_HOUR_RULE` | 集中培训 ≥3 天或 ≥24 学时 | 11 |
| `VOTE_RULE` | 支部大会双过半 | 15、23 |
| `BRANCH_RULE` | 转正讨论三出口 | 23 |
| `MATERIAL_REQUIRED_RULE` | 材料齐全性提醒 | 全部 |

**关于双过半**：流程图规定「到会人数必须超过应到会有表决权人数的半数才能开会；赞成人数超过应到会有表决权的正式党员的半数才能通过」。两个半数的分母**都是「应到」而不是「实到」** —— 这里写错的话，到场人数越少反而越容易通过，与制度设计意图正好相反。

新增或调整步骤只需改 `dev_step` 数据 + 必要时加一个策略类，主流程代码不动。

## 权限模型

- **功能权限**：`sys_menu` 树 + `sys_role_menu`，前端 `can(perm)`，后端 `@SaCheckPermission`
- **数据权限**：角色绑定 `data_scope`（1=全部 2=本级 3=本级及以下 4=仅本人 5=自定义），`sys_dept` 用物化路径 `org_path`（形如 `/1/3/7/`）前缀匹配整棵子树，无需递归查询
- **行级越权防护**：详情/修改/删除等按主键的操作统一用 `DataScopeHelper.canAccessOrg()` 校验

## 开发约定

- 模块依赖方向严格单向：`admin → develop/party/system → framework → common`，禁止反向依赖
- 新增业务模块时，表结构补进 `sql/01-schema.sql`，权限标识补进 `sql/02-init-system.sql` 的 `sys_menu`
- 实体类继承 `com.hparty.common.core.BaseEntity`（含 5 个审计字段）；表中缺任一审计列则改为 `implements Serializable` 并自行声明
- 列表查询调用 `DataScopeHelper.apply(wrapper)`；因 `LambdaQueryWrapper` 与 `QueryWrapper` 是兄弟类，该方法参数类型为 `AbstractWrapper`
- 关键写操作加 `@OperLog(title="模块", businessType=...)` 记录审计日志

## 已知待修

`sys_user.username` / `sys_role.role_key` 为逻辑删除列 + 唯一键，删除账号后重建同名会在数据库层撞唯一键（应用的唯一性校验只看 `del_flag=0`）。需改为物理删除，或把 `del_flag` 并入唯一键。
