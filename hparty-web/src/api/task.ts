import { http } from './request';

// ==================== 活动任务通知 ====================
// 对应后端 AmTaskController（基础路径 /party/task），即「任务卡 + 上传资料」。
// 任务类型与状态的取值、文案、颜色集中在这里，页面不再各自抄一份。

/** 活动类型取值与 meeting_type 一致，复用同一批中文名 */
export const TASK_TYPE_OPTIONS = [
  { label: '党员大会', value: 'MEMBER_ASSEMBLY' },
  { label: '支部委员会', value: 'BRANCH_COMMITTEE' },
  { label: '党小组会', value: 'PARTY_GROUP' },
  { label: '党课', value: 'PARTY_LECTURE' },
  { label: '主题党日', value: 'THEME_PARTY_DAY' },
  { label: '组织生活会', value: 'ORG_LIFE' },
];

export const TASK_TYPE_TEXT: Record<string, string> = Object.fromEntries(
  TASK_TYPE_OPTIONS.map((o) => [o.value, o.label]),
);

/** 任务状态，对应 am_task.status */
export const TASK_STATUS_OPTIONS = [
  { label: '草稿', value: 0 },
  { label: '已发布', value: 1 },
  { label: '已截止', value: 2 },
  { label: '已归档', value: 3 },
];

export const TASK_STATUS_TEXT: Record<number, string> = {
  0: '草稿',
  1: '已发布',
  2: '已截止',
  3: '已归档',
};

export const TASK_STATUS_COLORS: Record<number, string> = {
  0: 'default',
  1: 'processing',
  2: 'warning',
  3: 'default',
};

/** 活动任务通知，对应 am_task */
export interface AmTask {
  taskId: number;
  title: string;
  /** 活动类型，取值同 meeting_type */
  taskType: string;
  /** 发布单位；新增时由后端取当前账号归属组织，前端不传 */
  publishOrgId: number;
  publishOrgName?: string;
  activityName?: string;
  content?: string;
  startDate?: string;
  endDate?: string;
  /** 材料上传截止日期 */
  deadline?: string;
  /**
   * 接收组织ID，逗号分隔，NULL 表示全部下辖。
   *
   * 后端目前只在列表上按 `publish_org_id` 做数据权限，**并未据此过滤**，
   * 该列仅落库保存。表单暂不开放编辑，避免给出"设了能限范围"的错误预期。
   */
  receiveOrgIds?: string;
  /** 0=草稿 1=已发布 2=已截止 3=已归档 */
  status?: number;
  publishBy?: string;
  publishTime?: string;
  createTime?: string;
  updateTime?: string;
}

/** 发布 / 修改任务的表单载荷 */
export interface AmTaskForm {
  taskId?: number;
  title: string;
  taskType: string;
  activityName?: string;
  content?: string;
  startDate?: string;
  endDate?: string;
  deadline?: string;
  status?: number;
}

/** 任务提交记录，对应 am_task_submit */
export interface AmTaskSubmit {
  submitId: number;
  taskId: number;
  /** 提交组织 */
  orgId: number;
  fileId?: number;
  fileUrl?: string;
  remark?: string;
  submitBy?: string;
  submitTime?: string;
}

/** 任务列表，taskType 与 status 均可空（为空查全部） */
export const listTasks = (params?: { taskType?: string; status?: number }) =>
  http.get<AmTask[]>('/party/task/list', params);

/** 任务详情 */
export const getTask = (taskId: number) => http.get<AmTask>(`/party/task/${taskId}`);

/** 发布任务 */
export const addTask = (data: AmTaskForm) => http.post<number>('/party/task', data);

/** 修改任务 */
export const updateTask = (data: AmTaskForm) => http.put<void>('/party/task', data);

/** 删除任务 */
export const removeTask = (taskId: number) => http.delete<void>(`/party/task/${taskId}`);

/**
 * 支部上传任务材料。
 *
 * 必须走 multipart：后端该接口用 `@RequestParam` 逐个接收，表单字段名固定为
 * `taskId` / `file` / `remark`。换成 JSON body 会导致文件被静默丢弃
 * （接口仍返回成功，但落库恒为 NULL）。
 */
export const submitTaskMaterial = (taskId: number, file: File, remark?: string) => {
  const formData = new FormData();
  formData.append('taskId', String(taskId));
  formData.append('file', file);
  if (remark) {
    formData.append('remark', remark);
  }
  return http.upload<number>('/party/task/submit', formData);
};

/** 某任务的提交记录 */
export const listTaskSubmits = (taskId: number) =>
  http.get<AmTaskSubmit[]>(`/party/task/${taskId}/submits`);
