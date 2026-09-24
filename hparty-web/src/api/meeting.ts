import { http } from './request';

// ==================== 三会一课 ====================
// 党员大会 / 支部委员会 / 党小组会 / 党课 共用 am_meeting 表与 /party/meeting 接口，
// 用 meetingType 区分。主题党日（./partyday）复用同一批接口，只是固定了类型。

/** 会议记录，对应后端 AmMeetingVO */
export interface MeetingRecord {
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

/** 新增 / 修改会议的表单载荷 */
export interface MeetingForm {
  meetingId?: number;
  meetingType: string;
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

/** 会议列表（meetingType 为空查全部） */
export const listMeetings = (meetingType?: string) =>
  http.get<MeetingRecord[]>('/party/meeting/list', { meetingType });

/** 新增会议 */
export const addMeeting = (data: MeetingForm) => http.post<number>('/party/meeting', data);

/** 修改会议 */
export const updateMeeting = (data: MeetingForm) => http.put<void>('/party/meeting', data);

/** 删除会议 */
export const removeMeeting = (meetingId: number) =>
  http.delete<void>(`/party/meeting/${meetingId}`);

// 会议状态字典沿用 ./partyday —— 两者共用 /party/meeting 接口，状态含义完全一致，
// 不在此处再抄一份以免两处发散（后续若提取到公共文件，两边一起改）。
export {
  MEETING_STATUS_COLORS,
  MEETING_STATUS_OPTIONS,
  MEETING_STATUS_TEXT,
} from './partyday';
