import { http } from './request';
import type { PageQuery, PageResult } from '@/types/api';

// ==================== 先优评选 ====================

/** 评选候选人 */
export interface ExcellentCandidate {
  candidateId?: number;
  selectionId?: number;
  personId: number;
  personName?: string;
  orgId?: number;
  orgName?: string;
  /** 推荐党组织 */
  recommendOrgId?: number;
  recommendOrgName?: string;
  /** 主要事迹 */
  deeds?: string;
  votes?: number;
  rankNo?: number;
  /** 0=待评审 1=已推荐 2=已获奖 3=未获奖 */
  result?: number;
  resultLabel?: string;
}

/** 评选活动，对应后端 ExcellentSelectionVO */
export interface ExcellentSelection {
  selectionId: number;
  title: string;
  /** 1=优秀共产党员 2=优秀党务工作者 3=先进基层党组织 */
  selectionType?: number;
  selectionTypeLabel?: string;
  orgId?: number;
  orgName?: string;
  selectionYear?: number;
  startDate?: string;
  endDate?: string;
  /** 名额 */
  quota?: number;
  /** 0=草稿 1=推荐中 2=评审中 3=已公示 4=已表彰 */
  status?: number;
  statusLabel?: string;
  description?: string;
  /** 详情接口额外返回候选人名单 */
  candidates?: ExcellentCandidate[];
}

export interface ExcellentQuery extends PageQuery {
  orgId?: number;
  selectionType?: number;
  selectionYear?: number;
  status?: number;
}

/** 评选活动分页 */
export const pageExcellentSelections = (params: ExcellentQuery) =>
  http.get<PageResult<ExcellentSelection>>('/party/excellent/selection/page', params);

/** 评选活动详情（含候选人） */
export const getExcellentSelection = (selectionId: number) =>
  http.get<ExcellentSelection>(`/party/excellent/selection/${selectionId}`);

/** 新增评选活动 */
export const addExcellentSelection = (data: Partial<ExcellentSelection>) =>
  http.post<number>('/party/excellent/selection', data);

/** 修改评选活动 */
export const updateExcellentSelection = (data: Partial<ExcellentSelection>) =>
  http.put<void>('/party/excellent/selection', data);

/** 删除评选活动 */
export const removeExcellentSelection = (selectionId: number) =>
  http.delete<void>(`/party/excellent/selection/${selectionId}`);

/** 某评选活动的候选人名单 */
export const listExcellentCandidates = (selectionId: number) =>
  http.get<ExcellentCandidate[]>('/party/excellent/candidate/list', { selectionId });

/** 保存单个候选人 */
export const saveExcellentCandidate = (data: Partial<ExcellentCandidate>) =>
  http.post<number>('/party/excellent/candidate', data);

/** 删除候选人 */
export const removeExcellentCandidate = (candidateId: number) =>
  http.delete<void>(`/party/excellent/candidate/${candidateId}`);

/** 评选类型字典 */
export const EXCELLENT_TYPE_OPTIONS = [
  { label: '优秀共产党员', value: 1 },
  { label: '优秀党务工作者', value: 2 },
  { label: '先进基层党组织', value: 3 },
];

/** 评选状态字典 */
export const EXCELLENT_STATUS_OPTIONS = [
  { label: '草稿', value: 0 },
  { label: '推荐中', value: 1 },
  { label: '评审中', value: 2 },
  { label: '已公示', value: 3 },
  { label: '已表彰', value: 4 },
];

export const EXCELLENT_STATUS_COLORS: Record<number, string> = {
  0: 'default',
  1: 'processing',
  2: 'warning',
  3: 'cyan',
  4: 'success',
};

/** 评选结果字典 */
export const EXCELLENT_RESULT_OPTIONS = [
  { label: '待评审', value: 0 },
  { label: '已推荐', value: 1 },
  { label: '已获奖', value: 2 },
  { label: '未获奖', value: 3 },
];

export const EXCELLENT_RESULT_COLORS: Record<number, string> = {
  0: 'default',
  1: 'processing',
  2: 'success',
  3: 'error',
};
