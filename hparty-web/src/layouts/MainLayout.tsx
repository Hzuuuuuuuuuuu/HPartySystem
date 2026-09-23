import { useEffect, useMemo, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Avatar, Dropdown, Layout, Menu, Modal, Tabs, message } from 'antd';
import {
  LogoutOutlined,
  UserOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons';
import logo from '@/assets/logo.png';
import { useUserStore } from '@/store/user';
import { collectLeafTitles, findOpenKeys, toMenuItems } from './menuUtils';

const { Header, Sider, Content } = Layout;

interface TabItem {
  key: string;
  label: string;
}

/** 常驻标签页，不可关闭 */
const AFFIX_TAB: TabItem = { key: '/dashboard', label: '首页' };

export default function MainLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { userInfo, routers, logout } = useUserStore();

  const [collapsed, setCollapsed] = useState(false);
  const [openKeys, setOpenKeys] = useState<string[]>([]);
  const [tabs, setTabs] = useState<TabItem[]>([AFFIX_TAB]);

  const menuItems = useMemo(() => toMenuItems(routers), [routers]);
  const leafTitles = useMemo(() => collectLeafTitles(routers), [routers]);

  const currentPath = location.pathname;

  // 路由变化时同步标签页
  useEffect(() => {
    if (currentPath === '/' || currentPath === '/login') return;
    const title = leafTitles[currentPath];
    if (!title) return;

    setTabs((prev) =>
      prev.some((t) => t.key === currentPath) ? prev : [...prev, { key: currentPath, label: title }],
    );
  }, [currentPath, leafTitles]);

  // 自动展开当前项所在的父级菜单
  useEffect(() => {
    const keys = findOpenKeys(menuItems, currentPath);
    if (keys.length) {
      setOpenKeys((prev) => Array.from(new Set([...prev, ...keys])));
    }
  }, [currentPath, menuItems]);

  const handleLogout = () => {
    Modal.confirm({
      title: '确认退出登录？',
      okText: '退出',
      cancelText: '取消',
      onOk: async () => {
        await logout();
        message.success('已安全退出');
        navigate('/login', { replace: true });
      },
    });
  };

  const closeTab = (key: string) => {
    setTabs((prev) => {
      const next = prev.filter((t) => t.key !== key);
      // 关掉的是当前页时，跳到最后一个标签
      if (key === currentPath) {
        const fallback = next[next.length - 1] ?? AFFIX_TAB;
        navigate(fallback.key);
      }
      return next;
    });
  };

  return (
    <Layout className="hparty-root-layout">
      <Sider
        className="hparty-sider"
        width={212}
        collapsedWidth={0}
        collapsible
        collapsed={collapsed}
        trigger={null}
      >
        <div className="hparty-logo">
          <div className="hparty-logo__emblem">
            <img src={logo} alt="logo" />
          </div>
          <div className="hparty-logo__org">[{userInfo?.orgName ?? '未分配组织'}]</div>
        </div>

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
      </Sider>

      <Layout className="hparty-main-layout">
        <Header className="hparty-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
            <span
              className="hparty-header__user-item"
              onClick={() => setCollapsed((v) => !v)}
              style={{ padding: 6 }}
            >
              {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            </span>
            <div className="hparty-header__brand">
              <img src={logo} alt="logo" className="hparty-header__brand-icon" />
              <span>智慧党建</span>
            </div>
          </div>

          <div className="hparty-header__user">
            <Dropdown
              menu={{
                items: [
                  {
                    key: 'info',
                    icon: <UserOutlined />,
                    label: `${userInfo?.nickName ?? ''}（${userInfo?.username ?? ''}）`,
                    disabled: true,
                  },
                  { type: 'divider' },
                  { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', onClick: handleLogout },
                ],
              }}
            >
              <span className="hparty-header__user-item">
                <Avatar size={26} src={userInfo?.avatar} icon={<UserOutlined />} />
                <span>{userInfo?.nickName ?? userInfo?.username}</span>
              </span>
            </Dropdown>

            <span className="hparty-header__user-item" onClick={handleLogout}>
              <LogoutOutlined />
              <span>退出</span>
            </span>
          </div>
        </Header>

        {tabs.length > 1 && (
          <div className="hparty-tabs">
            <Tabs
              type="editable-card"
              hideAdd
              size="small"
              activeKey={currentPath}
              onChange={navigate}
              onEdit={(key, action) => {
                if (action === 'remove' && key !== AFFIX_TAB.key) {
                  closeTab(String(key));
                }
              }}
              items={tabs.map((t) => ({
                key: t.key,
                label: t.label,
                closable: t.key !== AFFIX_TAB.key,
              }))}
            />
          </div>
        )}

        <Content className="hparty-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
