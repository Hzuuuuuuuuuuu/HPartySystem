import { http } from './request';
import type { PageQuery, PageResult } from '@/types/api';

// ==================== 发展党员年度计划与指标（P1-4） ====================

/** 指标分解到下级组织的完成情况 */
export interface QuotaProgress {
  quotaId?: number;
  orgId: number;
  orgName?: string;
  quotaCount: number;
  reachedCount?: number;
  rate?: number;
  remark?: string;
}

/** 年度计划完成进度 */
export interface PlanProgress {
  planId?: number;
  orgId?: number;
  orgName?: string;
  planYear: number;
  planCount: number;
  activistTarget?: number;
  status?: number;
  /** 本年度已达到 STEP_07（确定发展对象）及之后的人数 */
  reachedCount?: number;
  rate?: number;
  quotas?: QuotaProgress[];
}

/** 年度计划 */
export interface DevPlan {
  planId: number;
  orgId: number;
  orgName?: string;
  planYear: number;
  planCount: number;
  activistTarget?: number;
  /** 0=草稿 1=已下达 2=执行中 3=已完成 */
  status?: number;
  statusLabel?: string;
  issueOrgId?: number;
  issueDate?: string;
  description?: string;
  quotas?: QuotaProgress[];
  progress?: PlanProgress;
}

export interface DevPlanQuery extends PageQuery {
  planYear?: number;
  orgId?: number;
  status?: number;
  keyword?: string;
}

/** 计划分页 */
export const pagePlans = (params: DevPlanQuery) =>
  http.get<PageResult<DevPlan>>('/develop/plan/page', params);

/** 计划详情（含指标分解） */
export const getPlan = (planId: number) => http.get<DevPlan>(`/develop/plan/${planId}`);

/** 计划完成进度 */
export const getPlanProgress = (params: { year?: number; orgId?: number }) =>
  http.get<PlanProgress>('/develop/plan/progress', params);

/** 下达计划 */
export const addPlan = (data: Partial<DevPlan>) => http.post<number>('/develop/plan', data);

/** 修改计划 */
export const updatePlan = (data: Partial<DevPlan>) => http.put<void>('/develop/plan', data);

/** 删除计划（仅草稿） */
export const removePlan = (planId: number) => http.delete<void>(`/develop/plan/${planId}`);

/** 分配指标（全量替换） */
export const savePlanQuotas = (
  planId: number,
  quotas: { orgId: number; quotaCount: number; remark?: string }[],
) => http.post<void>(`/develop/plan/${planId}/quota`, quotas);

/** 计划状态字典 */
export const PLAN_STATUS_OPTIONS = [
  { label: '草稿', value: 0 },
  { label: '已下达', value: 1 },
  { label: '执行中', value: 2 },
  { label: '已完成', value: 3 },
];

export const PLAN_STATUS_COLORS: Record<number, string> = {
  0: 'default',
  1: 'processing',
  2: 'warning',
  3: 'success',
};
