import { http } from './request';
import type { PageQuery, PageResult } from '@/types/api';

// ==================== 民主评议党员（P0-2） ====================

/** 三项完成率与优秀人数 */
export interface ReviewProgress {
  total: number;
  selfDone: number;
  peerDone: number;
  orgDone: number;
  excellentUsed: number;
  selfRate: number;
  peerRate: number;
  orgRate: number;
}

/** 评议批次 */
export interface ReviewBatch {
  reviewId: number;
  title: string;
  orgId?: number;
  orgName?: string;
  reviewYear: number;
  startDate?: string;
  endDate?: string;
  /** 0=草稿 1=自评中 2=互评中 3=组织评定中 4=已公示 5=已完成 */
  status?: number;
  statusLabel?: string;
  excellentQuota?: number;
  description?: string;
  progress?: ReviewProgress;
  details?: ReviewDetail[];
}

/** 评议明细（一人一条） */
export interface ReviewDetail {
  detailId: number;
  reviewId: number;
  personId: number;
  personName?: string;
  orgId: number;
  selfScore?: number;
  selfComment?: string;
  selfTime?: string;
  peerScore?: number;
  peerCount?: number;
  massScore?: number;
  orgScore?: number;
  totalScore?: number;
  grade?: number;
  orgComment?: string;
  dispose?: string;
}

export interface ReviewQuery extends PageQuery {
  reviewYear?: number;
  orgId?: number;
  status?: number;
  keyword?: string;
}

/** 等次分布统计 */
export interface ReviewStatistics {
  reviewId?: number;
  title?: string;
  total: number;
  graded: number;
  excellentQuota?: number;
  byGrade: { grade: number; gradeLabel: string; count: number; ratio: number }[];
  progress?: ReviewProgress;
}

/** 批次分页 */
export const pageReviews = (params: ReviewQuery) =>
  http.get<PageResult<ReviewBatch>>('/party/review/page', params);

/** 批次详情（含明细） */
export const getReview = (reviewId: number) =>
  http.get<ReviewBatch>(`/party/review/${reviewId}`);

/** 创建批次 */
export const addReview = (data: Partial<ReviewBatch>) =>
  http.post<number>('/party/review', data);

/** 修改批次 */
export const updateReview = (data: Partial<ReviewBatch>) =>
  http.put<void>('/party/review', data);

/** 删除批次（仅草稿） */
export const removeReview = (reviewId: number) =>
  http.delete<void>(`/party/review/${reviewId}`);

/** 启动评议（生成明细） */
export const startReview = (reviewId: number) =>
  http.post<number>(`/party/review/${reviewId}/start`);

/** 明细列表 */
export const listReviewDetails = (reviewId: number) =>
  http.get<ReviewDetail[]>(`/party/review/${reviewId}/details`);

/** 提交自评（本人） */
export const submitSelfEval = (reviewId: number, data: { selfScore: number; selfComment?: string }) =>
  http.post<void>(`/party/review/${reviewId}/self-eval`, data);

/** 提交互评（批量，不能给自己打分） */
export const submitPeerEval = (
  reviewId: number,
  items: { personId: number; score: number }[],
) => http.post<number>(`/party/review/${reviewId}/peer-eval`, { items });

/** 组织评定（批量） */
export const submitOrgEval = (
  reviewId: number,
  items: {
    detailId: number;
    orgScore?: number;
    massScore?: number;
    grade?: number;
    orgComment?: string;
    dispose?: string;
  }[],
) => http.post<number>(`/party/review/${reviewId}/org-eval`, { items });

/** 等次分布统计 */
export const getReviewStatistics = (reviewId?: number) =>
  http.get<ReviewStatistics>('/party/review/statistics', { reviewId });

/** 批次状态字典 */
export const REVIEW_STATUS_OPTIONS = [
  { label: '草稿', value: 0 },
  { label: '自评中', value: 1 },
  { label: '互评中', value: 2 },
  { label: '组织评定中', value: 3 },
  { label: '已公示', value: 4 },
  { label: '已完成', value: 5 },
];

export const REVIEW_STATUS_COLORS: Record<number, string> = {
  0: 'default',
  1: 'processing',
  2: 'processing',
  3: 'warning',
  4: 'cyan',
  5: 'success',
};

/** 评议等次字典 */
export const REVIEW_GRADE_OPTIONS = [
  { label: '优秀', value: 1 },
  { label: '合格', value: 2 },
  { label: '基本合格', value: 3 },
  { label: '不合格', value: 4 },
];

export const REVIEW_GRADE_COLORS: Record<number, string> = {
  1: 'red',
  2: 'green',
  3: 'orange',
  4: 'default',
};

/** 不合格党员的处置意见选项 */
export const DISPOSE_OPTIONS = [
  { label: '限期改正', value: '限期改正' },
  { label: '劝退', value: '劝退' },
  { label: '除名', value: '除名' },
];
