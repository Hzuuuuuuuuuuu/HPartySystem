/** 后端统一响应体，与 com.hparty.common.core.R 对应 */
export interface ApiResult<T = unknown> {
  code: number;
  msg: string;
  data: T;
  timestamp: number;
}

/** 分页结果，与 com.hparty.common.core.PageResult 对应 */
export interface PageResult<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
  pages: number;
}

/** 分页查询基类，与 com.hparty.common.core.PageQuery 对应 */
export interface PageQuery {
  pageNum?: number;
  pageSize?: number;
  orderByColumn?: string;
  isAsc?: 'asc' | 'desc';
}

/** 业务状态码 */
export const ResultCode = {
  SUCCESS: 200,
  PARAM_ERROR: 400,
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  NOT_FOUND: 404,
  FAIL: 500,
  BIZ_ERROR: 600,
  /** 流程规则校验未通过 */
  RULE_REJECT: 601,
} as const;
