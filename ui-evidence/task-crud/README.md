# 活动任务通知（/meeting/task）界面验证证据

本目录是「活动任务 CRUD」这次改动的界面验证截图与生成脚本。
截图取自演示数据，不含真实个人数据。

| 文件 | 验证内容 |
|---|---|
| `01-list-toolbar.png` | 支部书记视角的列表与工具栏（刷新 / 详情 / 编辑 / 删除 / 发布任务） |
| `02-add-modal.png` | 发布任务弹窗初始态 |
| `03-add-date-validation.png` | 结束日期早于开始日期时的跨字段校验提示 |
| `04-add-filled.png` | 表单填写完整、可提交 |
| `05-add-success.png` | 发布成功提示，列表出现新任务 |
| `06-edit-modal.png` | 编辑弹窗按选中行预填 |
| `07-edit-success.png` | 修改成功提示，标题已更新 |
| `08-detail-drawer-empty.png` | 详情抽屉：字段完整，提交记录为空 |
| `09-submit-success.png` | 上传资料成功提示 |
| `10-detail-drawer-with-submit.png` | 上传后详情抽屉出现 1 条提交记录，状态推进为「已截止」 |
| `11-delete-confirm.png` | 删除二次确认弹窗 |
| `12-delete-success.png` | 删除成功提示，列表中该行消失 |
| `13-list-readonly-no-write-buttons.png` | 仅有 `task:list` 的账号：工具栏无编辑/删除/发布任务，操作列无「上传资料」 |
| `14-member-cannot-open-page.png` | 无 `task` 菜单的账号直接敲 URL：命中 404，左侧菜单也没有该入口 |

## 复跑

前置条件（脚本不负责启动服务）：

1. 后端在 `http://localhost:8080/api`
2. 前端 dev server 在 `http://localhost:5173`
3. Redis 在 `127.0.0.1:6379`，且与后端是**同一个实例**

```bash
npm i -g playwright                                               # 首次，走全局，不进仓库依赖
npx playwright install chromium                                   # 首次
node ui-evidence/task-crud/capture.mjs
```

`playwright` 故意不写进 `hparty-web/package.json`：它只是截图工具，不该给应用引入
构建期依赖。脚本默认用 `npm root -g` 定位全局包，装在别处时用 `PLAYWRIGHT_ROOT`
指向其 `node_modules` 目录。可用 `WEB_BASE` / `API_BASE` / `REDIS_HOST` / `REDIS_PORT`
覆盖默认地址。

## 为什么走 API 登录

后端 `hparty.captcha.enabled=true`，图形验证码无法自动识别，但验证码原文就存在
Redis（`hparty:captcha:<uuid>`，2 分钟过期），脚本直接读出来。
另一个原因是登录失败 5 次会锁定账号 10 分钟（`Constants.LOGIN_FAIL_LIMIT`），
不适合在登录页上试错。脚本用的账号：`zsf`（有写权限）、`liming`（仅 `task:list`）、
`zhaoxue`（无 `task` 菜单），密码均为演示数据密码。

## 运行后的数据残留

脚本会往**当前连接的数据库**写入真实数据，跑完不会自动清理干净，请知悉：

| 位置 | 残留 | 说明 |
|---|---|---|
| `am_task` | 1 行，`del_flag=1` | 脚本末尾执行了删除，是**逻辑删除**，行还在，只是列表查不到 |
| `am_task_submit` | 1 行 | 提交记录不随任务删除而清除 |
| `sys_file` | 1 行 | 同上，文件登记记录保留 |
| `D:/hparty/upload/task_material/<yyyyMMdd>/` | 1 个 `<uuid>.txt` | 上传的临时文件，物理文件不会随逻辑删除消失 |
| `sys_oper_log` | 若干行 | 本次新增的 `@OperLog` 会记录发布 / 修改 / 删除 / 上传四类操作 |

脚本临时写在本机 `os.tmpdir()` 的源文件（`task-ui-evidence-<ts>.txt`）会在结束时删除。
任务标题统一带 `UI验证-` 前缀和时间戳，便于按前缀检索或清理。

上表的数据库部分是**依据代码路径推断**的，没有直连数据库逐行核对（本机没有可用的
MySQL 客户端凭据）；磁盘那一条已实测确认。需要精确清理时请以实际查库结果为准。
