import { http } from './request';
import type { PageQuery, PageResult } from '@/types/api';

// ==================== 党员教育管理 ====================

/** 参学人员 */
export interface EducationParticipant {
  participantId?: number;
  activityId?: number;
  personId: number;
  personName?: string;
  /** 0=未参加 1=已参加 2=请假 3=缺席 */
  attendStatus?: number;
  studyHours?: number;
  score?: number;
  /** 1=合格 0=不合格 */
  isPassed?: number;
}

/** 教育活动，对应后端 EducationActivityVO */
export interface EducationActivity {
  activityId: number;
  title: string;
  /** 1=党课 2=专题培训 3=在线学习 4=实践锻炼 5=集中轮训 */
  activityType?: number;
  activityTypeLabel?: string;
  orgId?: number;
  orgName?: string;
  organizer?: string;
  startDate?: string;
  endDate?: string;
  studyHours?: number;
  place?: string;
  teacher?: string;
  content?: string;
  shouldAttend?: number;
  actualAttend?: number;
  /** 0=草稿 1=报名中 2=进行中 3=已结束 */
  status?: number;
  statusLabel?: string;
}

export interface EducationQuery extends PageQuery {
  orgId?: number;
  activityType?: number;
  status?: number;
  keyword?: string;
}

/** 教育活动统计 */
export interface EducationStatistics {
  totalActivities: number;
  totalHours: number;
  totalParticipants: number;
  byType: { type: number; typeLabel: string; count: number }[];
}

/** 教育活动分页 */
export const pageEducations = (params: EducationQuery) =>
  http.get<PageResult<EducationActivity>>('/party/education/page', params);

/** 教育活动详情 */
export const getEducation = (activityId: number) =>
  http.get<EducationActivity>(`/party/education/${activityId}`);

/** 新增教育活动 */
export const addEducation = (data: Partial<EducationActivity>) =>
  http.post<number>('/party/education', data);

/** 修改教育活动 */
export const updateEducation = (data: Partial<EducationActivity>) =>
  http.put<void>('/party/education', data);

/** 删除教育活动 */
export const removeEducation = (activityId: number) =>
  http.delete<void>(`/party/education/${activityId}`);

/** 参学人员名单 */
export const listEducationParticipants = (activityId: number) =>
  http.get<EducationParticipant[]>(`/party/education/${activityId}/participants`);

/** 保存参学人员名单（整体覆盖） */
export const saveEducationParticipants = (
  activityId: number,
  participants: EducationParticipant[],
) => http.post<void>(`/party/education/${activityId}/participants`, participants);

/** 党员教育统计 */
export const getEducationStatistics = () =>
  http.get<EducationStatistics>('/party/education/statistics');

/** 教育类型字典 */
export const EDUCATION_TYPE_OPTIONS = [
  { label: '党课', value: 1 },
  { label: '专题培训', value: 2 },
  { label: '在线学习', value: 3 },
  { label: '实践锻炼', value: 4 },
  { label: '集中轮训', value: 5 },
];

/** 教育状态字典 */
export const EDUCATION_STATUS_OPTIONS = [
  { label: '草稿', value: 0 },
  { label: '报名中', value: 1 },
  { label: '进行中', value: 2 },
  { label: '已结束', value: 3 },
];

export const EDUCATION_STATUS_COLORS: Record<number, string> = {
  0: 'default',
  1: 'warning',
  2: 'processing',
  3: 'success',
};

/** 参学状态字典 */
export const ATTEND_STATUS_OPTIONS = [
  { label: '未参加', value: 0 },
  { label: '已参加', value: 1 },
  { label: '请假', value: 2 },
  { label: '缺席', value: 3 },
];
