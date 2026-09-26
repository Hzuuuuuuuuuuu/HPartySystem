import { Suspense, lazy, useMemo } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { Spin } from 'antd';
import MainLayout from '@/layouts/MainLayout';
import { useUserStore } from '@/store/user';
import type { RouterVO } from '@/api/auth';

/** 所有页面组件，供后端下发的 component 路径动态匹配 */
const pageModules = import.meta.glob('../pages/**/*.tsx');

/**
 * 把后端的 component 字符串解析成懒加载组件。
 *
 * 菜单表里有些 component 带了查询串（如 `meeting/index?type=MEMBER_ASSEMBLY`），
 * 直接拿去拼文件名会查不到任何模块，路由被静默跳过、点击菜单落到 404。
 * 这里按 `?` 截断只取组件路径部分；筛选项由页面自己从 URL 路径推导。
 */
function resolveComponent(component?: string) {
  if (!component || component === 'Layout') return null;

  const componentPath = component.split('?')[0].trim();
  if (!componentPath) return null;

  const key = `../pages/${componentPath}.tsx`;
  const loader = pageModules[key];
  if (!loader) {
    console.warn(`[router] 未找到页面组件：${key}，请检查菜单配置的 component 字段`);
    return null;
  }
  return lazy(loader as () => Promise<{ default: React.ComponentType }>);
}

function PageLoading() {
  // antd 的 Spin 只有在嵌套模式或全屏模式下才支持 tip，这里用普通布局承载文案
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: 12,
        padding: '80px 0',
      }}
    >
      <Spin size="large" />
      <span style={{ color: '#8C8C8C' }}>加载中...</span>
    </div>
  );
}

interface FlatRoute {
  path: string;
  component: string;
  title: string;
}

/** 把路由树拍平成 path → component 的列表 */
function flattenRouters(routers: RouterVO[], parentPath = '', acc: FlatRoute[] = []): FlatRoute[] {
  for (const router of routers) {
    const fullPath =
      router.path === '/'
        ? parentPath
        : `${parentPath.replace(/\/$/, '')}/${router.path.replace(/^\//, '')}`;

    const children = router.children ?? [];
    if (children.length > 0) {
      flattenRouters(children, fullPath, acc);
    } else if (router.component) {
      acc.push({
        path: fullPath === '' ? '/' : fullPath,
        component: router.component,
        title: router.meta?.title ?? '',
      });
    }
  }
  return acc;
}

export default function AppRoutes() {
  const { token, routers, can } = useUserStore();

  const flatRoutes = useMemo(() => flattenRouters(routers), [routers]);

  if (!token) {
    return (
      <Routes>
        <Route
          path="/login"
          element={
            <Suspense fallback={<PageLoading />}>
              <LoginPage />
            </Suspense>
          }
        />
        <Route
          path="/register"
          element={
            <Suspense fallback={<PageLoading />}>
              <RegisterPage />
            </Suspense>
          }
        />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    );
  }

  return (
    <Routes>
      <Route path="/login" element={<Navigate to="/dashboard" replace />} />
      <Route path="/" element={<MainLayout />}>
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="dashboard" element={<DashboardPage />} />

        {flatRoutes.map((route) => {
          const Component = resolveComponent(route.component);
          if (!Component) return null;
          return (
            <Route
              key={route.path}
              path={route.path.replace(/^\//, '')}
              element={
                <Suspense fallback={<PageLoading />}>
                  <Component />
                </Suspense>
              }
            />
          );
        })}

        {/* 发展党员 25 步详情：不在菜单里，由卡片墙跳转进入 */}
        {can('develop:applicant:detail') && (
          <Route
            path="develop/applicant/:applicantId"
            element={
              <Suspense fallback={<PageLoading />}>
                <ApplicantDetailPage />
              </Suspense>
            }
          />
        )}

        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

const LoginPage = lazy(() => import('@/pages/login'));
const RegisterPage = lazy(() => import('@/pages/register'));
const DashboardPage = lazy(() => import('@/pages/dashboard'));
const NotFoundPage = lazy(() => import('@/pages/error/404'));
const ApplicantDetailPage = lazy(() => import('@/pages/develop/ApplicantDetail'));
