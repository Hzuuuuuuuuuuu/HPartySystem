# 左侧菜单选中态与滚动隔离优化设计

## 背景

当前前端存在两个明确问题：

1. 进入“发展党员 > 阶段统计”等子菜单后，父级“发展党员”进入 Ant Design `submenu-selected` 状态。现有暗色菜单主题将选中文字配置为红色，但父级背景仍为红色，造成红底红字、可读性严重下降。
2. 左侧菜单与右侧内容区都具备滚动能力，但页面外层仍可参与滚动，且滚动边界会发生 scroll chaining。用户在左侧滚到底部后继续滚动时，可能带动右侧或页面外层；反之亦然。

## 目标

- 当前选中的叶子菜单继续使用“白底红字”。
- 其父级菜单保持“红底白字”，仅通过展开态/图标方向表达层级，不因子项选中而变成红字。
- 左侧菜单和右侧内容区成为两个独立滚动容器。
- 鼠标或触控板位于左侧菜单时，只滚动左侧；即使到达顶部/底部，也不把剩余滚动传递到右侧。
- 鼠标或触控板位于右侧内容区时，只滚动右侧；即使到达顶部/底部，也不把剩余滚动传递到左侧或页面外层。
- 不引入 JS `wheel` 事件劫持，不新增第三方滚动组件。

## 方案

采用纯 CSS 的滚动隔离与菜单状态覆盖。

### 1. 菜单选中态

保留 Ant Design 叶子项的主题配置：

- `darkItemSelectedBg = #FFFFFF`
- `darkItemSelectedColor = #C7000B`

对侧边栏父级菜单增加更精确的覆盖：

- `.hparty-sider .ant-menu-submenu-selected > .ant-menu-submenu-title` 保持白色文字。
- 父级标题 hover 仍使用浅色透明背景，不改变文字为红色。
- 真正选中的 `.ant-menu-item-selected` 继续白底红字。

这样只突出当前页面，不让父级与子项同时形成高强度选中态。

### 2. 页面高度与滚动边界

将应用根节点固定在视口高度：

- `html, body, #root` 保持 `height: 100%`。
- `body` 设置 `overflow: hidden`，禁止浏览器页面级滚动。
- 主 Layout 使用 `height: 100vh`、`min-height: 0`，不再依赖仅有 `minHeight: 100vh` 的可扩张布局。

右侧主 Layout 设置 `height: 100%`、`min-height: 0`、`overflow: hidden`，避免 Flex 子项因为默认 `min-height: auto` 撑开父容器，导致滚动回落到 body。

### 3. 左侧滚动容器

由侧边栏菜单区域承担纵向滚动：

- Sider 本身保持固定视口高度。
- Logo 区固定，不参与滚动。
- Menu 使用剩余高度并 `overflow-y: auto`，高度由 Sider 的视口高度扣除 Logo 区得到。
- 设置 `overscroll-behavior-y: contain`，阻断到边界后的滚动链传播。
- 保持 `overflow-x: hidden`。

### 4. 右侧滚动容器

右侧布局拆分为固定头部/标签栏和独立内容滚动区：

- Header 固定在右侧布局顶部，通过 Flex 布局保持 `flex: none`。
- Tabs 作为普通固定高度区域，同样不参与内容滚动。
- Content 使用 `flex: 1`、`min-height: 0`、`overflow: auto`。
- Content 设置 `overscroll-behavior: contain`，阻断滚动链传播。

横向滚动仍由各 Table 自身的 Ant Design `scroll.x` 负责，不改变现有业务页面表格行为。

## 影响文件

预计仅需要修改：

- `hparty-web/src/layouts/MainLayout.tsx`
- `hparty-web/src/styles/global.css`

`src/styles/theme.ts` 不需要大改，仍保留叶子菜单白底红字的全局主题定义；父级状态通过侧边栏作用域 CSS 精确覆盖，避免影响其他 Menu 组件。

## 验证方式

1. `npx tsc --noEmit`
2. `npm run build`
3. 浏览器实际验证：
   - 打开“发展党员 > 阶段统计”，确认“阶段统计”为白底红字，“发展党员”为红底白字。
   - 左侧菜单足够长时，鼠标位于左侧滚动至底部后继续滚轮，右侧内容位置不变化。
   - 鼠标位于右侧内容滚动至底部后继续滚轮，左侧菜单位置不变化。
   - Header、Tabs 不随右侧内容滚动。
   - 表格横向滚动仍正常。

## 非目标

本次不调整整体视觉品牌、不重构菜单数据结构、不引入虚拟滚动、不修改业务页面内部 Table/Modal 的独立滚动行为。
