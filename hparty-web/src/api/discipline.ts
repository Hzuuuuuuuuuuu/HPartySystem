import { http } from './request';
import type { PageQuery, PageResult } from '@/types/api';

// ==================== 党纪学习教育 ====================

/** 参学人员 */
export interface DisciplineParticipant {
  participantId?: number;
  studyId?: number;
  personId: number;
  personName?: string;
  /** 0=未参加 1=已参加 2=请假 3=缺席 */
  attendStatus?: number;
  score?: number;
  /** 1=合格 0=不合格 */
  isPassed?: number;
}

/** 党纪学习记录，对应后端 DisciplineStudyVO */
export interface DisciplineStudy {
  studyId: number;
  title: string;
  /** 1=条例学习 2=警示教育 3=专题党课 4=知识测试 5=案例研讨 */
  studyType?: number;
  studyTypeLabel?: string;
  orgId?: number;
  orgName?: string;
  studyDate?: string;
  place?: string;
  teacher?: string;
  content?: string;
  participantCount?: number;
  passCount?: number;
  status?: number;
  statusLabel?: string;
}

export interface DisciplineQuery extends PageQuery {
  orgId?: number;
  studyType?: number;
  keyword?: string;
}

/** 党纪学习分页 */
export const pageDisciplines = (params: DisciplineQuery) =>
  http.get<PageResult<DisciplineStudy>>('/party/discipline/page', params);

/** 党纪学习详情 */
export const getDiscipline = (studyId: number) =>
  http.get<DisciplineStudy>(`/party/discipline/${studyId}`);

/** 新增党纪学习 */
export const addDiscipline = (data: Partial<DisciplineStudy>) =>
  http.post<number>('/party/discipline', data);

/** 修改党纪学习 */
export const updateDiscipline = (data: Partial<DisciplineStudy>) =>
  http.put<void>('/party/discipline', data);

/** 删除党纪学习 */
export const removeDiscipline = (studyId: number) =>
  http.delete<void>(`/party/discipline/${studyId}`);

/** 参学人员名单 */
export const listDisciplineParticipants = (studyId: number) =>
  http.get<DisciplineParticipant[]>(`/party/discipline/${studyId}/participants`);

/** 保存参学人员名单（整体覆盖） */
export const saveDisciplineParticipants = (
  studyId: number,
  participants: DisciplineParticipant[],
) => http.post<void>(`/party/discipline/${studyId}/participants`, participants);

/** 党纪学习类型字典 */
export const DISCIPLINE_TYPE_OPTIONS = [
  { label: '条例学习', value: 1 },
  { label: '警示教育', value: 2 },
  { label: '专题党课', value: 3 },
  { label: '知识测试', value: 4 },
  { label: '案例研讨', value: 5 },
];

/** 参学状态字典 */
export const DISCIPLINE_ATTEND_OPTIONS = [
  { label: '未参加', value: 0 },
  { label: '已参加', value: 1 },
  { label: '请假', value: 2 },
  { label: '缺席', value: 3 },
];
