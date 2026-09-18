import { create } from 'zustand';
import { clearToken, getToken, setToken } from '@/api/request';
import type { LoginVO, RouterVO, UserInfoVO } from '@/api/auth';
import * as authApi from '@/api/auth';

interface UserState {
  token: string;
  userInfo: UserInfoVO | null;
  routers: RouterVO[];
  /** 是否已拉取过用户信息与路由 */
  loaded: boolean;

  /** 登录。返回完整的 LoginVO —— 调用方需要读 needChangePwd 判断是否强制改密 */
  login: (params: {
    username: string;
    password: string;
    code?: string;
    uuid?: string;
  }) => Promise<LoginVO>;
  loadUserInfo: () => Promise<void>;
  logout: () => Promise<void>;
  /** 只清本地登录态，不调后端 */
  clearLocal: () => void;
  /** 是否拥有某个权限标识 */
  hasPerm: (perm: string) => boolean;
  /** 是否拥有某个角色 */
  hasRole: (role: string) => boolean;
  /** 按钮级权限判断：超级管理员放行，或权限集合中含该标识 */
  can: (perm?: string) => boolean;
}

export const useUserStore = create<UserState>((set, get) => ({
  token: getToken(),
  userInfo: null,
  routers: [],
  loaded: false,

  login: async (params) => {
    const res = await authApi.login(params);
    setToken(res.token);
    set({ token: res.token });
    return res;
  },

  /** 仅清理本地登录态，不调用后端（用于会话已被服务端失效的场景，如改密后） */
  clearLocal: () => {
    clearToken();
    set({ token: '', userInfo: null, routers: [], loaded: false });
  },

  loadUserInfo: async () => {
    const [userInfo, routers] = await Promise.all([authApi.getUserInfo(), authApi.getRouters()]);
    set({ userInfo, routers, loaded: true });
  },

  logout: async () => {
    try {
      await authApi.logout();
    } finally {
      clearToken();
      set({ token: '', userInfo: null, routers: [], loaded: false });
    }
  },

  hasPerm: (perm) => {
    const { userInfo } = get();
    if (!userInfo) return false;
    if (userInfo.superAdmin) return true;
    return userInfo.perms?.includes(perm) ?? false;
  },

  hasRole: (role) => {
    const { userInfo } = get();
    if (!userInfo) return false;
    return userInfo.roles?.includes(role) ?? false;
  },

  can: (perm) => {
    if (!perm) return true;
    const { userInfo } = get();
    if (!userInfo) return false;
    if (userInfo.superAdmin) return true;
    return userInfo.perms?.includes(perm) ?? false;
  },
}));
