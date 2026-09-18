import { http } from './request';

// ==================== 主题党日 ====================
// 主题党日复用三会一课的会议接口，仅固定 meetingType = THEME_PARTY_DAY

/** 主题党日记录，对应后端 AmMeetingVO */
export interface ThemePartyDay {
  meetingId: number;
  meetingType: string;
  meetingTypeLabel?: string;
  title: string;
  orgId?: number;
  orgName?: string;
  content?: string;
  meetingDate?: string;
  startTime?: string;
  endTime?: string;
  place?: string;
  hostName?: string;
  recorderName?: string;
  shouldAttend?: number;
  actualAttend?: number;
  status?: number;
  statusLabel?: string;
}

export interface ThemePartyDayForm {
  meetingId?: number;
  title: string;
  meetingDate?: string;
  startTime?: string;
  endTime?: string;
  place?: string;
  hostName?: string;
  recorderName?: string;
  shouldAttend?: number;
  actualAttend?: number;
  status?: number;
  content?: string;
}

/** 主题党日记录列表 */
export const listThemePartyDays = () =>
  http.get<ThemePartyDay[]>('/party/meeting/list', { meetingType: 'THEME_PARTY_DAY' });

/** 新增主题党日 */
export const addThemePartyDay = (data: ThemePartyDayForm) =>
  http.post<number>('/party/meeting', { ...data, meetingType: 'THEME_PARTY_DAY' });

/** 修改主题党日 */
export const updateThemePartyDay = (data: ThemePartyDayForm) =>
  http.put<void>('/party/meeting', { ...data, meetingType: 'THEME_PARTY_DAY' });

/** 删除主题党日 */
export const removeThemePartyDay = (meetingId: number) =>
  http.delete<void>(`/party/meeting/${meetingId}`);

/** 会议状态字典 */
export const MEETING_STATUS_OPTIONS = [
  { label: '草稿', value: 0 },
  { label: '待召开', value: 1 },
  { label: '进行中', value: 2 },
  { label: '已结束', value: 3 },
  { label: '已归档', value: 4 },
];

export const MEETING_STATUS_COLORS: Record<number, string> = {
  0: 'default',
  1: 'processing',
  2: 'processing',
  3: 'success',
  4: 'default',
};

/** 状态中文名 */
export const MEETING_STATUS_TEXT: Record<number, string> = {
  0: '草稿',
  1: '待召开',
  2: '进行中',
  3: '已结束',
  4: '已归档',
};
