import { http } from './request';
import type { PageQuery, PageResult } from '@/types/api';

// ==================== 党费收缴及使用 ====================

/** 党费收缴记录，对应后端 DuesRecordVO */
export interface DuesRecord {
  duesId: number;
  personId?: number;
  personName?: string;
  orgId?: number;
  orgName?: string;
  duesYear?: number;
  duesMonth?: number;
  /** 党费计算基数 */
  duesBase?: number;
  /** 缴纳标准（比例或金额） */
  duesStandard?: number;
  /** 实缴金额 */
  duesPaid?: number;
  payDate?: string;
  /** 1=现金 2=转账 3=代扣 4=微信 5=支付宝 */
  payType?: number;
  payTypeLabel?: string;
  /** 0=未缴 1=已缴 2=免缴 3=补缴 */
  status?: number;
  statusLabel?: string;
  /** 是否逾期 */
  isOverdue?: boolean | number;
}

/** 党费使用记录 */
export interface DuesUse {
  useId: number;
  orgId?: number;
  orgName?: string;
  useYear?: number;
  useMonth?: number;
  amount?: number;
  /** 1=党员教育 2=表彰奖励 3=困难帮扶 4=阵地建设 5=订阅报刊 6=其它 */
  useCategory?: number;
  useCategoryLabel?: string;
  purpose?: string;
  useDate?: string;
  approver?: string;
}

export interface DuesRecordQuery extends PageQuery {
  orgId?: number;
  duesYear?: number;
  duesMonth?: number;
  status?: number;
  personName?: string;
}

export interface DuesUseQuery {
  orgId?: number;
  useYear?: number;
}

/** 月度收缴统计项 */
export interface DuesMonthStat {
  month: number;
  shouldTotal: number;
  paidTotal: number;
  paidCount: number;
  unpaidCount: number;
}

/** 年度收缴统计 */
export interface DuesStatistics {
  year: number;
  shouldTotal: number;
  paidTotal: number;
  unpaidCount: number;
  paidCount: number;
  months: DuesMonthStat[];
}

// ---------- 收缴记录 ----------

/** 收缴记录分页 */
export const pageDuesRecords = (params: DuesRecordQuery) =>
  http.get<PageResult<DuesRecord>>('/party/dues/record/page', params);

/** 新增收缴记录 */
export const addDuesRecord = (data: Partial<DuesRecord>) =>
  http.post<number>('/party/dues/record', data);

/** 修改收缴记录 */
export const updateDuesRecord = (data: Partial<DuesRecord>) =>
  http.put<void>('/party/dues/record', data);

/** 删除收缴记录 */
export const removeDuesRecord = (duesId: number) =>
  http.delete<void>(`/party/dues/record/${duesId}`);

/** 批量生成某月党费账单 */
export const generateDuesRecords = (year: number, month: number) =>
  http.post<void>(`/party/dues/record/generate?year=${year}&month=${month}`);

/** 年度收缴统计 */
export const getDuesStatistics = (year: number) =>
  http.get<DuesStatistics>('/party/dues/record/statistics', { year });

// ---------- 使用记录 ----------

/** 使用记录列表 */
export const listDuesUses = (params: DuesUseQuery) =>
  http.get<DuesUse[]>('/party/dues/use/list', params);

/** 新增使用记录 */
export const addDuesUse = (data: Partial<DuesUse>) =>
  http.post<number>('/party/dues/use', data);

/** 删除使用记录 */
export const removeDuesUse = (useId: number) =>
  http.delete<void>(`/party/dues/use/${useId}`);

// ---------- 字典 ----------

/** 缴纳状态字典 */
export const DUES_STATUS_OPTIONS = [
  { label: '未缴', value: 0 },
  { label: '已缴', value: 1 },
  { label: '免缴', value: 2 },
  { label: '补缴', value: 3 },
];

export const DUES_STATUS_COLORS: Record<number, string> = {
  0: 'error',
  1: 'success',
  2: 'default',
  3: 'warning',
};

/** 缴纳方式字典 */
export const DUES_PAY_TYPE_OPTIONS = [
  { label: '现金', value: 1 },
  { label: '银行转账', value: 2 },
  { label: '工资代扣', value: 3 },
  { label: '微信', value: 4 },
  { label: '支付宝', value: 5 },
];

/** 党费用途字典 */
export const DUES_USE_CATEGORY_OPTIONS = [
  { label: '党员教育', value: 1 },
  { label: '表彰奖励', value: 2 },
  { label: '困难帮扶', value: 3 },
  { label: '阵地建设', value: 4 },
  { label: '订阅报刊', value: 5 },
  { label: '其它', value: 6 },
];

/** 月份选项 */
export const MONTH_OPTIONS = Array.from({ length: 12 }, (_, i) => ({
  label: `${i + 1} 月`,
  value: i + 1,
}));
