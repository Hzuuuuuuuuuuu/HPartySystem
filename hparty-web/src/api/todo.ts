import { http } from './request';

// ==================== 我的待办 ====================

/** 单条待办 */
export interface TodoItem {
  /** 稳定标识，如 dev-2-STEP_02 */
  key: string;
  title: string;
  description?: string;
  deadline?: string;
  overdue?: boolean;
  overdueDays?: number;
  /** 点击跳转的前端路由 */
  link?: string;
}

/** 待办分组 */
export interface TodoGroup {
  type: string;
  typeLabel: string;
  count: number;
  /** /my/todo/count 不返回明细 */
  items?: TodoItem[];
}

export interface TodoResult {
  total: number;
  overdueTotal: number;
  groups: TodoGroup[];
}

/** 我的待办（按来源分组） */
export const getMyTodo = () => http.get<TodoResult>('/my/todo');

/** 我的待办数量（首页卡片/红点） */
export const getMyTodoCount = () => http.get<TodoResult>('/my/todo/count');

/** 分组编码 → 展示色，供标签与图标使用 */
export const TODO_GROUP_COLORS: Record<string, string> = {
  DEVELOP: '#C7000B',
  TASK: '#1890FF',
  DUES: '#FAAD14',
  TRANSFER: '#52C41A',
};
