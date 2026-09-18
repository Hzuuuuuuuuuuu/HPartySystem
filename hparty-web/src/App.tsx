import { useEffect, useState } from 'react';
import { BrowserRouter } from 'react-router-dom';
import { App as AntdApp, ConfigProvider, Spin } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';

import AppRoutes from '@/router/AppRoutes';
import { antdTheme } from '@/styles/theme';
import { useUserStore } from '@/store/user';

dayjs.locale('zh-cn');

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
      staleTime: 30_000,
    },
  },
});

export default function App() {
  const { token, loaded, loadUserInfo, logout } = useUserStore();
  const [booting, setBooting] = useState(true);

  // 应用启动时：有令牌则拉取用户信息与动态路由
  useEffect(() => {
    if (!token || loaded) {
      setBooting(false);
      return;
    }

    loadUserInfo()
      .catch(() => {
        // 令牌失效，清理后回到登录页
        logout().catch(() => undefined);
      })
      .finally(() => setBooting(false));
  }, [token, loaded, loadUserInfo, logout]);

  if (booting) {
    return (
      <div
        style={{
          height: '100vh',
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
          alignItems: 'center',
          justifyContent: 'center',
          background: '#C7000B',
          color: '#fff',
        }}
      >
        <Spin size="large" />
        <div style={{ fontSize: 15, letterSpacing: 1 }}>智慧党建管理系统 加载中...</div>
      </div>
    );
  }

  return (
    <ConfigProvider locale={zhCN} theme={antdTheme}>
      <AntdApp>
        <QueryClientProvider client={queryClient}>
          <BrowserRouter>
            <AppRoutes />
          </BrowserRouter>
        </QueryClientProvider>
      </AntdApp>
    </ConfigProvider>
  );
}
