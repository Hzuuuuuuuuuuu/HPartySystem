import { http } from './request';
import type { PageQuery, PageResult } from '@/types/api';

// ==================== 组织关系转接 ====================

/** 流转时间线节点 */
export interface TransferTimelineNode {
  title: string;
  status: string;
  operator?: string;
  time?: string;
  description?: string;
}

/** 组织关系转接单，对应后端 PartyTransferService#toVO */
export interface PartyTransfer {
  transferId: number;
  transferNo?: string;
  /** 1=转出 2=转入 3=内部调整 */
  transferType?: number;
  transferTypeLabel?: string;
  personId?: number;
  personName?: string;
  fromOrgId?: number;
  fromOrgName?: string;
  toOrgId?: number;
  toOrgName?: string;
  reason?: string;
  letterNo?: string;
  letterDate?: string;
  validDays?: number;
  expireDate?: string;
  /** 0=待提交 1=已开具 2=已接收 3=已拒绝 4=已超期 5=已撤销 */
  status?: number;
  statusLabel?: string;
  /** 读取时现算的超期判定，不依赖 status=4 */
  overdue?: boolean;
  overdueDays?: number;
  transferDate?: string;
  handlerId?: number;
  handlerName?: string;
  rejectReason?: string;
  fileId?: number;
  fileUrl?: string;
  remark?: string;
  createTime?: string;
  /** 仅详情接口返回 */
  timeline?: TransferTimelineNode[];
}

export interface TransferQuery extends PageQuery {
  transferType?: number;
  status?: number;
  personName?: string;
  transferNo?: string;
  fromOrgId?: number;
  toOrgId?: number;
  beginDate?: string;
  endDate?: string;
}

/** 转接分页 */
export const pageTransfers = (params: TransferQuery) =>
  http.get<PageResult<PartyTransfer>>('/party/transfer/page', params);

/** 转接详情（含流转时间线） */
export const getTransfer = (transferId: number) =>
  http.get<PartyTransfer>(`/party/transfer/${transferId}`);

/** 超期未落地清单（口袋党员） */
export const listOverdueTransfers = (params?: TransferQuery) =>
  http.get<PartyTransfer[]>('/party/transfer/overdue', params);

/** 发起转接 */
export const addTransfer = (data: Partial<PartyTransfer>) =>
  http.post<number>('/party/transfer', data);

/** 修改转接（仅待提交） */
export const updateTransfer = (data: Partial<PartyTransfer>) =>
  http.put<void>('/party/transfer', data);

/** 开具介绍信 */
export const issueTransfer = (transferId: number) =>
  http.post<void>(`/party/transfer/${transferId}/issue`);

/** 接收（组织关系变更） */
export const acceptTransfer = (transferId: number) =>
  http.post<void>(`/party/transfer/${transferId}/accept`);

/** 拒绝接收 */
export const rejectTransfer = (transferId: number, reason: string) =>
  http.post<void>(`/party/transfer/${transferId}/reject`, { reason });

/** 撤销 */
export const revokeTransfer = (transferId: number) =>
  http.post<void>(`/party/transfer/${transferId}/revoke`);

/** 删除（仅待提交） */
export const removeTransfer = (transferId: number) =>
  http.delete<void>(`/party/transfer/${transferId}`);

/** 转接类型字典 */
export const TRANSFER_TYPE_OPTIONS = [
  { label: '转出', value: 1 },
  { label: '转入', value: 2 },
  { label: '内部调整', value: 3 },
];

/** 转接状态字典 */
export const TRANSFER_STATUS_OPTIONS = [
  { label: '待提交', value: 0 },
  { label: '已开具', value: 1 },
  { label: '已接收', value: 2 },
  { label: '已拒绝', value: 3 },
  { label: '已超期', value: 4 },
  { label: '已撤销', value: 5 },
];

/** 状态颜色：0=灰 1=蓝 2=绿 3=红 4=橙 5=灰 */
export const TRANSFER_STATUS_COLORS: Record<number, string> = {
  0: 'default',
  1: 'blue',
  2: 'success',
  3: 'error',
  4: 'orange',
  5: 'default',
};
