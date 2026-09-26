import { http } from './request';

/** 登录用户信息，与后端 UserInfoVO 对应 */
export interface UserInfoVO {
  userId: number;
  username: string;
  nickName: string;
  avatar?: string;
  personId?: number;
  personName?: string;
  orgId?: number;
  orgName?: string;
  orgType?: number;
  orgPath?: string;
  dataScope?: number;
  superAdmin: boolean;
  roles: string[];
  perms: string[];
}

/** 动态路由，与后端 RouterVO 对应 */
export interface RouterVO {
  name?: string;
  path: string;
  hidden?: boolean;
  redirect?: string;
  component?: string;
  meta?: {
    title?: string;
    icon?: string;
    noCache?: boolean;
    perms?: string;
  };
  children?: RouterVO[];
}

export interface CaptchaVO {
  uuid: string | null;
  img: string | null;
  enabled: boolean;
}

export interface LoginVO {
  token: string;
  tokenName: string;
  expiresIn: number;
  /** 是否需要立即修改密码（从未改过初始密码，或密码已过期） */
  needChangePwd?: boolean;
  /** 需要改密时的中文原因 */
  changePwdReason?: string;
  /** 密码有效期（天） */
  passwordExpireDays?: number;
}

export const getCaptcha = () => http.get<CaptchaVO>('/auth/captcha');

export const login = (data: {
  username: string;
  password: string;
  code?: string;
  uuid?: string;
}) => http.post<LoginVO>('/auth/login', data);

export const logout = () => http.post<void>('/auth/logout');

/**
 * 用户自助修改密码。
 * 服务端会校验原密码与强度；成功后当前会话立即失效，需用新密码重新登录。
 */
export const changePassword = (data: { oldPassword: string; newPassword: string }) =>
  http.post<void>('/auth/changePassword', data);

/**
 * 用户注册（入党申请人自助注册）。
 */
export const register = (data: {
  username: string;
  password: string;
  confirmPassword: string;
  name: string;
  gender?: number;
  idCard?: string;
  phone: string;
  code: string;
  uuid: string;
}) => http.post<void>('/auth/register', data);

export const getUserInfo = () => http.get<UserInfoVO>('/auth/userInfo');

export const getRouters = () => http.get<RouterVO[]>('/auth/routers');
