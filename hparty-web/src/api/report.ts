import { http } from './request';

// ==================== 统计报表导出（P1-5） ====================

/**
 * 触发浏览器下载。
 *
 * `http.download` 返回的是原始响应（blob + headers），文件名优先从
 * `Content-Disposition` 的 `filename*=UTF-8''...` 里取，取不到再用兜底名。
 */
const saveBlob = (blob: Blob, fallbackName: string, disposition?: string) => {
  let fileName = `${fallbackName}.xlsx`;
  if (disposition) {
    const star = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
    const plain = /filename="?([^";]+)"?/i.exec(disposition);
    if (star?.[1]) {
      try {
        fileName = decodeURIComponent(star[1]);
      } catch {
        fileName = star[1];
      }
    } else if (plain?.[1]) {
      fileName = plain[1];
    }
  }
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
};

const download = async (url: string, params: unknown, fallbackName: string) => {
  const res = await http.download(url, params);
  saveBlob(res.data, fallbackName, res.headers?.['content-disposition']);
};

/** 导出党员名册 */
export const exportMember = (params?: Record<string, unknown>) =>
  download('/report/export/member', params, '党员名册');

/** 导出党费收缴台账 */
export const exportDues = (params?: Record<string, unknown>) =>
  download('/report/export/dues', params, '党费收缴台账');

/** 导出发展党员进度表 */
export const exportDevelop = (params?: Record<string, unknown>) =>
  download('/report/export/develop', params, '发展党员进度表');

/** 导出三会一课开展情况 */
export const exportMeeting = (params?: Record<string, unknown>) =>
  download('/report/export/meeting', params, '三会一课开展情况');

/** 导出民主评议党员结果 */
export const exportReview = (params?: Record<string, unknown>) =>
  download('/report/export/review', params, '民主评议党员结果');
