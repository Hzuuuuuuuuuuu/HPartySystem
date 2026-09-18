# Sidebar Menu and Scroll Isolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复左侧父级菜单选中态可读性，并将左侧菜单与右侧内容区改为互不串联的独立滚动容器。

**Architecture:** 保留 Ant Design 现有菜单数据结构和主题配置，仅在 `MainLayout` 上补充稳定的布局 class，并在 `global.css` 中建立视口固定、Flex 高度收缩、独立 overflow 与 `overscroll-behavior` 规则。叶子菜单仍由 Ant Design theme 负责白底红字，父级 submenu 选中态仅在 `.hparty-sider` 作用域内覆盖为白字。

**Tech Stack:** React 18、TypeScript、Ant Design 5、CSS、Vite 5。

## Global Constraints

- 不引入 JS `wheel` 事件劫持。
- 不新增第三方滚动组件。
- “阶段统计”等当前叶子菜单保持白底红字。
- 父级“发展党员”等 submenu 在子项选中时保持红底白字。
- 左右滚动区域互不串联，边界处不得把滚动传递给另一 panel 或 body。
- Header 与 Tabs 不随右侧内容区滚动。
- 保持现有 Table `scroll.x` 横向滚动行为。

---

### Task 1: 固定主布局滚动边界

**Files:**
- Modify: `hparty-web/src/layouts/MainLayout.tsx`
- Modify: `hparty-web/src/styles/global.css`

**Interfaces:**
- Consumes: 现有 `.hparty-sider`、`.hparty-content`、`.hparty-header`、`.hparty-tabs` class。
- Produces: `.hparty-root-layout`、`.hparty-main-layout`、`.hparty-menu` 布局 class。

- [ ] **Step 1: 修改 MainLayout 的结构 class**

将根布局改为：

```tsx
<Layout className="hparty-root-layout">
```

将 Sider 内 Menu 增加 class，移除内联滚动样式：

```tsx
<Menu
  className="hparty-menu"
  theme="dark"
  mode="inline"
  items={menuItems}
  selectedKeys={[currentPath]}
  openKeys={collapsed ? [] : openKeys}
  onOpenChange={setOpenKeys}
  onClick={({ key }) => navigate(key)}
/>
```

将右侧 Layout 改为：

```tsx
<Layout className="hparty-main-layout">
```

- [ ] **Step 2: 建立固定视口和独立滚动容器 CSS**

在 `global.css` 中使页面级滚动停止参与：

```css
html,
body,
#root {
  height: 100%;
  margin: 0;
  padding: 0;
}

body {
  overflow: hidden;
}

.hparty-root-layout {
  height: 100vh;
  min-height: 0 !important;
  overflow: hidden;
}

.hparty-main-layout {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}
```

左侧与右侧分别承担滚动：

```css
.hparty-sider {
  height: 100vh;
  overflow: hidden;
}

.hparty-menu {
  max-height: calc(100vh - 156px);
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior-y: contain;
}

.hparty-header,
.hparty-tabs {
  flex: none;
}

.hparty-content {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  overscroll-behavior: contain;
}
```

- [ ] **Step 3: 静态验证布局代码**

Run:

```powershell
cd D:\java\HPartySystem\hparty-web
npx tsc --noEmit
```

Expected: exit code 0，无 TypeScript error。

### Task 2: 修复父级菜单选中态

**Files:**
- Modify: `hparty-web/src/styles/global.css`

**Interfaces:**
- Consumes: Ant Design `.ant-menu-submenu-selected`、`.ant-menu-submenu-title`、`.ant-menu-item-selected` class。
- Produces: 仅作用于 `.hparty-sider` 的父级菜单可读性覆盖。

- [ ] **Step 1: 添加 submenu-selected 颜色覆盖**

```css
.hparty-sider .ant-menu-submenu-selected > .ant-menu-submenu-title,
.hparty-sider .ant-menu-submenu-selected > .ant-menu-submenu-title .ant-menu-item-icon,
.hparty-sider .ant-menu-submenu-selected > .ant-menu-submenu-title .ant-menu-submenu-arrow {
  color: #fff !important;
}
```

保留现有 `.ant-menu-item-selected` 的 Ant Design theme 行为，不覆盖叶子项红字白底。

- [ ] **Step 2: 生产构建验证**

Run:

```powershell
cd D:\java\HPartySystem\hparty-web
npm run build
```

Expected: `tsc -b && vite build` 成功，exit code 0。

### Task 3: 实际页面交互验证

**Files:**
- Verify only: runtime at `http://localhost:5173`

**Interfaces:**
- Consumes: 当前运行的 Vite 前端与后端服务。
- Produces: 菜单可读性与滚动隔离的运行时验证结论。

- [ ] **Step 1: 确认前端服务仍可访问**

Run:

```powershell
Invoke-WebRequest http://localhost:5173 -UseBasicParsing
```

Expected: HTTP 200。

- [ ] **Step 2: 浏览器验证菜单选中态**

进入“发展党员 > 阶段统计”，确认：

```text
发展党员：红色侧栏背景 + 白色文字
阶段统计：白色选中背景 + 红色文字
```

- [ ] **Step 3: 浏览器验证滚动隔离**

确认：

```text
鼠标位于左侧菜单 -> 仅左侧滚动；到达边界继续滚动，右侧 scrollTop 不变。
鼠标位于右侧内容 -> 仅右侧滚动；到达边界继续滚动，左侧 scrollTop 不变。
Header/Tabs 保持原位。
```

- [ ] **Step 4: 验证表格横向滚动不受影响**

打开带 `scroll.x` 的页面（例如阶段统计），确认表格横向滚动仍由表格自身承担。

- [ ] **Step 5: 最终复核**

重新执行：

```powershell
npx tsc --noEmit
npm run build
```

Expected: 两条命令均成功。

> 当前项目目录无 `.git` 元数据，因此本计划不执行 git commit 步骤。
