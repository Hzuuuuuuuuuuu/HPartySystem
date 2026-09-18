import axios, { type AxiosError, type AxiosRequestConfig } from 'axios';
import { message } from 'antd';
import { ResultCode, type ApiResult } from '@/types/api';

/** 令牌在 localStorage 中的键名 */
export const TOKEN_KEY = 'hparty_token';

export const getToken = () => localStorage.getItem(TOKEN_KEY) ?? '';
export const setToken = (token: string) => localStorage.setItem(TOKEN_KEY, token);
export const clearToken = () => localStorage.removeItem(TOKEN_KEY);

const instance = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json;charset=utf-8' },
});

/** 避免登录过期时弹出多个提示 */
let redirecting = false;

instance.interceptors.request.use(
  (config) => {
    const token = getToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error),
);

instance.interceptors.response.use(
  (response) => {
    // 文件流直接透传，交由调用方处理
    if (response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') {
      return response;
    }

    const res = response.data as ApiResult;

    if (res.code === ResultCode.SUCCESS) {
      // 解包，调用方直接拿到 data
      return res.data as never;
    }

    if (res.code === ResultCode.UNAUTHORIZED) {
      handleUnauthorized();
      return Promise.reject(new Error(res.msg));
    }

    // 流程规则未通过用 warning，与系统错误区分开
    if (res.code === ResultCode.RULE_REJECT) {
      message.warning(res.msg, 6);
      return Promise.reject(new Error(res.msg));
    }

    message.error(res.msg || '请求失败');
    return Promise.reject(new Error(res.msg));
  },
  (error: AxiosError) => {
    const status = error.response?.status;

    if (status === 401) {
      handleUnauthorized();
      return Promise.reject(error);
    }

    let text = '网络异常，请稍后重试';
    if (status === 403) text = '没有操作权限';
    else if (status === 404) text = '请求的接口不存在';
    else if (status === 500) text = '服务器内部错误';
    else if (error.code === 'ECONNABORTED') text = '请求超时，请稍后重试';
    else if (!error.response) text = '无法连接服务器，请确认后端已启动';

    message.error(text);
    return Promise.reject(error);
  },
);

function handleUnauthorized() {
  if (redirecting) return;
  redirecting = true;
  clearToken();
  message.error('登录已过期，请重新登录');
  setTimeout(() => {
    window.location.href = '/login';
    redirecting = false;
  }, 800);
}

/** 类型友好的请求方法：拦截器已解包，返回值即业务数据 */
export const http = {
  get: <T>(url: string, params?: unknown, config?: AxiosRequestConfig) =>
    instance.get<unknown, T>(url, { params, ...config }),
  post: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
    instance.post<unknown, T>(url, data, config),
  put: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
    instance.put<unknown, T>(url, data, config),
  delete: <T>(url: string, config?: AxiosRequestConfig) =>
    instance.delete<unknown, T>(url, config),
  /** 下载文件，返回原始响应以便读取 Content-Disposition */
  download: (url: string, params?: unknown) =>
    instance.get<unknown, { data: Blob; headers: Record<string, string> }>(url, {
      params,
      responseType: 'blob',
    }),
  /** 鉴权预览文件。若后端以 JSON Blob 返回业务异常，统一转回正常错误处理。 */
  preview: async (url: string) => {
    const response = await instance.get<unknown, { data: Blob; headers: Record<string, string> }>(url, {
      responseType: 'blob',
    });
    const contentType = response.headers['content-type'] ?? response.data.type ?? '';
    if (contentType.includes('application/json')) {
      let res: ApiResult | undefined;
      try {
        res = JSON.parse(await response.data.text()) as ApiResult;
      } catch {
        // 保留统一兜底文案。
      }
      if (res?.code === ResultCode.UNAUTHORIZED) {
        handleUnauthorized();
      } else {
        message.error(res?.msg || '文件预览失败');
      }
      throw new Error(res?.msg || '文件预览失败');
    }
    return response;
  },
  /** 上传文件 */
  upload: <T>(url: string, formData: FormData) =>
    instance.post<unknown, T>(url, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }),
};

export default instance;
