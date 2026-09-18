import { http } from './request';
import type { PageQuery, PageResult } from '@/types/api';

// ==================== 党员服务 ====================

/** 党员服务记录，对应后端 PartyServiceVO */
export interface PartyService {
  serviceId: number;
  title: string;
  /** 1=困难帮扶 2=志愿服务 3=走访慰问 4=权益维护 5=就业帮扶 6=其它 */
  serviceType?: number;
  serviceTypeLabel?: string;
  personId?: number;
  personName?: string;
  orgId?: number;
  orgName?: string;
  serviceDate?: string;
  content?: string;
  amount?: number;
  handlerName?: string;
  /** 0=待处理 1=处理中 2=已完成 3=已取消 */
  status?: number;
  statusLabel?: string;
  result?: string;
}

export interface PartyServiceQuery extends PageQuery {
  orgId?: number;
  serviceType?: number;
  status?: number;
  personName?: string;
  keyword?: string;
}

/** 服务统计 */
export interface ServiceStatistics {
  total: number;
  byType: { type: number | string; typeLabel?: string; count: number }[];
  byStatus: { status: number | string; statusLabel?: string; count: number }[];
  totalAmount: number;
}

/** 服务记录分页 */
export const pageServices = (params: PartyServiceQuery) =>
  http.get<PageResult<PartyService>>('/party/service/page', params);

/** 服务详情 */
export const getService = (serviceId: number) =>
  http.get<PartyService>(`/party/service/${serviceId}`);

/** 新增服务记录 */
export const addService = (data: Partial<PartyService>) =>
  http.post<number>('/party/service', data);

/** 修改服务记录 */
export const updateService = (data: Partial<PartyService>) =>
  http.put<void>('/party/service', data);

/** 删除服务记录 */
export const removeService = (serviceId: number) =>
  http.delete<void>(`/party/service/${serviceId}`);

/** 服务统计 */
export const getServiceStatistics = () =>
  http.get<ServiceStatistics>('/party/service/statistics');

/** 服务类型字典 */
export const SERVICE_TYPE_OPTIONS = [
  { label: '困难帮扶', value: 1 },
  { label: '志愿服务', value: 2 },
  { label: '走访慰问', value: 3 },
  { label: '权益维护', value: 4 },
  { label: '就业帮扶', value: 5 },
  { label: '其它', value: 6 },
];

/** 服务状态字典 */
export const SERVICE_STATUS_OPTIONS = [
  { label: '待处理', value: 0 },
  { label: '处理中', value: 1 },
  { label: '已完成', value: 2 },
  { label: '已取消', value: 3 },
];

export const SERVICE_STATUS_COLORS: Record<number, string> = {
  0: 'warning',
  1: 'processing',
  2: 'success',
  3: 'default',
};
