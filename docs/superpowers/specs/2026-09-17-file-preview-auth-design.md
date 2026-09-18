# 文件预览鉴权修复设计

## 背景

当前 `/file/preview/{bizType}/{date}/{fileName}` 被加入 `SaTokenConfig.WHITE_LIST`，匿名用户可直接读取文件字节。后端预览逻辑仅依赖 UUID 文件名不可枚举和路径规范化，不校验登录态及 `sys_file.org_id` 数据权限。前端已有入口通过 `window.open(fileUrl)` 或 `<a href={fileUrl}>` 直接访问，因此简单移除白名单会导致现有预览功能失效。

## 目标

1. 匿名用户不能直接读取 `/file/preview/**`。
2. 已登录用户只能预览其数据权限范围内的文件。
3. 前端现有发展党员材料、组织关系转接文件预览继续可用。
4. 不改变现有 Sa-Token Header 模式，不引入 Cookie/CSRF 模型，不新增第三方依赖。
5. 预览 URL 不再被视为访问凭证；真正授权依赖登录态与文件元数据。

## 方案

采用“登录鉴权 + 文件资源级授权 + 前端鉴权 Blob 预览”。

### 后端

- 从 `SaTokenConfig.WHITE_LIST` 移除 `/file/preview/**`。
- `FileController.preview()` 增加显式 `@SaCheckLogin`，与全局登录拦截器形成双重防护。
- `SysFileService.preview()` 根据规范化后的 `file_path` 查询 `sys_file`：
  - 没有有效文件元数据时直接返回“文件不存在”，不再降级为直接读取磁盘；
  - 找到记录后调用现有 `checkOrgAccess()`，沿用 `DataScopeHelper.canAccessOrg()` 数据权限；
  - 权限通过后仅从记录的 `file_path` 读取内容，并返回原始文件名/MIME。
- 保持路径分段校验和 `FileStorage` 路径规范化，不降低现有路径穿越防护。

### 前端

- 在现有请求层增加鉴权 Blob 获取能力，不复制 Token 处理逻辑。
- 新增统一文件预览工具 `openFilePreview(fileUrl)`：
  - 接受后端返回的 `/api/file/preview/...` URL；
  - 归一化为 Axios `baseURL=/api` 下的请求路径；
  - 使用现有 Axios 实例发送 `responseType=blob`，自动携带 `Authorization: Bearer ...`；
  - 成功后 `URL.createObjectURL(blob)` 并通过新窗口打开；
  - 短延时后 `URL.revokeObjectURL()` 释放资源；
  - 不把 token 放入 query string。
- 替换 `ApplicantDetail.tsx` 中直接 `window.open(m.fileUrl)`。
- 替换 `transfer/index.tsx` 中直接 `href={detail.fileUrl}`。

## 授权边界

- 匿名访问：401/未登录，不进入文件读取。
- 登录且文件 `org_id` 在当前用户数据权限内：允许。
- 登录但文件 `org_id` 不在数据权限内：拒绝。
- `org_id IS NULL` 的历史/内部文件暂沿用现有下载口径：登录后可读取；不在本次变更中扩大语义。
- 逻辑删除的 `sys_file` 因 MyBatis-Plus `@TableLogic` 查询条件不应被预览。
- 磁盘存在但 `sys_file` 无记录：不允许预览，防止绕过资源权限。

## 兼容性

当前前端实际预览入口均为点击后打开文件，没有发现生产代码使用 `<img src={fileUrl}>` 直接绑定受保护预览 URL，因此改为 Blob 打开不会破坏已知页面。未来若需要 `<img>/<video>` 内联展示，应复用鉴权 Blob/object URL 组件，不重新开放匿名 URL。

## 测试策略

后端新增永久回归测试，使用真实 Spring MVC + Sa-Token + MySQL/Redis：

1. 匿名请求已存在文件的 preview URL，被拒绝且不能读取字节。
2. 同组织/授权范围用户请求同一文件，返回 200、原始字节和正确 Content-Type。
3. 跨组织/无数据权限用户请求同一文件，被拒绝。
4. 只有磁盘文件但无 `sys_file` 元数据时，即使登录也不能读取。

前端执行 `npm run lint` 与 `npm run build`；运行态再验证匿名预览失败、登录后合法预览成功以及 5173 `/api` 代理正常。

## 非目标

- 不实现 HMAC 签名 URL。
- 不改为 Cookie 登录态。
- 不重构所有业务模块的文件上传权限。
- 不处理生产管理员初始密码问题。
