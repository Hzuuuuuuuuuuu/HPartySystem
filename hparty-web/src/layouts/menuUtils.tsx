import type { MenuProps } from 'antd';
import type { RouterVO } from '@/api/auth';
import { resolveIcon } from '@/components/IconRegistry';

type MenuItem = Required<MenuProps>['items'][number];

/**
 * 按名称取图标组件。
 * <p>走显式注册表而非整包导入 —— 后者会把 500+ 个图标全打进产物。</p>
 */
export function renderIcon(name?: string) {
  const Icon = resolveIcon(name);
  return Icon ? <Icon /> : undefined;
}

/** 拼接父子路径 */
export function joinPath(parent: string, child: string): string {
  if (!child) return parent;
  if (child.startsWith('/')) return child;
  const base = parent.endsWith('/') ? parent.slice(0, -1) : parent;
  return `${base}/${child}`;
}

/**
 * 把后端返回的路由树转成 Ant Design Menu 的 items。
 *
 * 后端对「顶层菜单」会包一层 path="/" 的 Layout，这里需要拆掉，
 * 否则侧边栏会出现一层无意义的父节点。
 */
export function toMenuItems(routers: RouterVO[], parentPath = ''): MenuItem[] {
  const items: MenuItem[] = [];

  for (const router of routers) {
    if (router.hidden) continue;

    // 拆掉 path="/" 的 Layout 包装层
    if (router.path === '/' && router.children?.length) {
      items.push(...toMenuItems(router.children, parentPath));
      continue;
    }

    const fullPath = joinPath(parentPath, router.path);
    const children = router.children ?? [];

    if (children.length > 0) {
      items.push({
        key: fullPath,
        icon: renderIcon(router.meta?.icon),
        label: router.meta?.title ?? fullPath,
        children: toMenuItems(children, fullPath),
      });
    } else {
      items.push({
        key: fullPath,
        icon: renderIcon(router.meta?.icon),
        label: router.meta?.title ?? fullPath,
      });
    }
  }

  return items;
}

/** 收集所有叶子路由的 path → title，用于标签页标题 */
export function collectLeafTitles(
  routers: RouterVO[],
  parentPath = '',
  acc: Record<string, string> = {},
): Record<string, string> {
  for (const router of routers) {
    const fullPath = router.path === '/' ? parentPath : joinPath(parentPath, router.path);
    const children = router.children ?? [];
    if (children.length > 0) {
      collectLeafTitles(children, fullPath, acc);
    } else if (router.meta?.title) {
      acc[fullPath] = router.meta.title;
    }
  }
  return acc;
}

/** 找到某个路径对应的菜单展开父级 key */
export function findOpenKeys(items: MenuItem[], target: string, trail: string[] = []): string[] {
  for (const item of items) {
    if (!item) continue;
    const key = String(item.key);
    const children = (item as { children?: MenuItem[] }).children;
    if (children?.length) {
      const found = findOpenKeys(children, target, [...trail, key]);
      if (found.length) return found;
    } else if (key === target) {
      return trail;
    }
  }
  return [];
}
