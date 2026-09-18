import { http } from './request';
import type { PageQuery, PageResult } from '@/types/api';

// ==================== 党组织换届 ====================

/** 换届候选人 */
export interface ElectionCandidate {
  candidateId?: number;
  electionId?: number;
  personId: number;
  personName?: string;
  positionCode?: string;
  positionName?: string;
  /** 1=现任 0=非现任 */
  isIncumbent?: number;
  votes?: number;
  /** 1=当选 0=未当选 */
  isElected?: number;
}

/** 换届活动，对应后端 ElectionVO */
export interface Election {
  electionId: number;
  orgId?: number;
  orgName?: string;
  /** 1=换届选举 2=补选 3=委员调整 */
  electionType?: number;
  electionTypeLabel?: string;
  termNo?: number;
  title: string;
  reason?: string;
  planDate?: string;
  electionDate?: string;
  place?: string;
  hostName?: string;
  shouldAttend?: number;
  actualAttend?: number;
  /** 0=筹备中 1=进行中 2=已完成 3=已终止 */
  status?: number;
  statusLabel?: string;
  resultSummary?: string;
  /** 详情接口额外返回候选人名单 */
  candidates?: ElectionCandidate[];
}

export interface ElectionQuery extends PageQuery {
  orgId?: number;
  status?: number;
  keyword?: string;
}

/** 换届分页 */
export const pageElections = (params: ElectionQuery) =>
  http.get<PageResult<Election>>('/party/election/page', params);

/** 换届详情（含候选人） */
export const getElection = (electionId: number) =>
  http.get<Election>(`/party/election/${electionId}`);

/** 新增换届 */
export const addElection = (data: Partial<Election>) =>
  http.post<number>('/party/election', data);

/** 修改换届 */
export const updateElection = (data: Partial<Election>) =>
  http.put<void>('/party/election', data);

/** 删除换届 */
export const removeElection = (electionId: number) =>
  http.delete<void>(`/party/election/${electionId}`);

/** 保存候选人名单（整体覆盖） */
export const saveElectionCandidates = (
  electionId: number,
  candidates: ElectionCandidate[],
) => http.post<void>(`/party/election/${electionId}/candidates`, candidates);

/** 换届类型字典 */
export const ELECTION_TYPE_OPTIONS = [
  { label: '换届选举', value: 1 },
  { label: '补选', value: 2 },
  { label: '委员调整', value: 3 },
];

/** 换届状态字典 */
export const ELECTION_STATUS_OPTIONS = [
  { label: '筹备中', value: 0 },
  { label: '进行中', value: 1 },
  { label: '已完成', value: 2 },
  { label: '已终止', value: 3 },
];

export const ELECTION_STATUS_COLORS: Record<number, string> = {
  0: 'default',
  1: 'processing',
  2: 'success',
  3: 'error',
};

/** 委员岗位字典 */
export const ELECTION_POSITION_OPTIONS = [
  { label: '支部书记', value: 'SECRETARY' },
  { label: '副书记', value: 'DEPUTY_SECRETARY' },
  { label: '组织委员', value: 'ORG_COMMITTEE' },
  { label: '宣传委员', value: 'PROP_COMMITTEE' },
  { label: '纪检委员', value: 'DISC_COMMITTEE' },
  { label: '青年委员', value: 'YOUTH_COMMITTEE' },
];
