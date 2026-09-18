# 文件预览鉴权修复实施计划

## 目标

按已批准方案 A 完成 `/file/preview/**` 从匿名 URL 到“登录 + 数据权限 + 前端 Blob 鉴权预览”的切换，并保留现有合法预览体验。

## Task 1：后端安全回归测试（RED）

新增 `hparty-admin/src/test/java/com/hparty/security/FilePreviewSecurityRegressionTest.java`，用真实 Spring MVC + MySQL/Redis 构造测试用户和 `sys_file`：

- 匿名访问已存在文件 preview URL 应被拒绝；
- 同组织授权用户应可读取；
- 无权组织用户应被拒绝；
- 磁盘存在但无 `sys_file` 元数据时登录用户也不能读取。

先运行该测试，确认当前实现至少匿名访问和跨组织访问断言失败，形成有效 RED。

## Task 2：后端实现（GREEN）

修改：

- `hparty-framework/.../SaTokenConfig.java`
  - 移除 `/file/preview/**` 白名单。
- `hparty-system/.../FileController.java`
  - `preview()` 增加 `@SaCheckLogin`；
  - 更新注释，删除“UUID 即防越权”的旧表述。
- `hparty-system/.../SysFileService.java`
  - `preview()` 必须查询 `sys_file`；
  - 未找到记录直接抛“文件不存在”；
  - 调用 `checkOrgAccess()`；
  - 通过 `readEntity()` 读取。

重跑 `FilePreviewSecurityRegressionTest`，要求全部通过。

## Task 3：前端鉴权 Blob 预览

修改 `hparty-web/src/api/request.ts`：

- 新增返回 Blob 原始响应的 `preview`/blob GET 能力。

新增 `hparty-web/src/utils/filePreview.ts`：

- 将绝对/相对 `/api/file/...` URL 归一化为 Axios baseURL 下请求；
- 通过现有 `http` 请求实例带 Token 获取 Blob；
- `URL.createObjectURL` + `window.open`；
- 延迟 revoke object URL；
- 异常由现有 Axios 拦截器统一处理。

修改：

- `pages/develop/ApplicantDetail.tsx`
- `pages/transfer/index.tsx`

所有受保护文件预览不再直接 `window.open(fileUrl)` / `href=fileUrl`。

## Task 4：自动化与静态验证

运行：

```powershell
mvn -f hparty-server/pom.xml test
npm --prefix hparty-web run lint
npm --prefix hparty-web run build
```

要求后端全量测试、前端类型检查和生产构建全部成功。

## Task 5：运行态验证

重建并重启最新后端，保持前端 5173 运行，验证：

- 匿名访问 `/api/file/preview/...` 不再拿到文件内容；
- 登录用户合法文件预览可返回 200；
- 跨组织文件预览被拒绝；
- `http://127.0.0.1:5173/` 正常；
- 5173 `/api` 代理正常。

## Task 6：文档与收尾

更新：

- `docs/01-系统设计.md`
- `docs/04-接口清单.md`
- `docs/06-生产上线清单.md`
- `docs/08-全系统回归巡检报告.md`

删除“预览免登录 / UUID 不可枚举即防越权”的旧描述，记录正式修复与验证结果。最后按 `verification-before-completion` 做新鲜全量验证，再完成 AgentDock final review。
