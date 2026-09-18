import { http } from '@/api/request';

/**
 * 通过现有 Axios 鉴权链路打开受保护文件。
 *
 * 后端返回的 fileUrl 通常是 /api/file/preview/...；Axios 的 baseURL 已经是 /api，
 * 因此这里只把同源 URL 归一化为 /file/preview/...，避免重复拼成 /api/api/...
 */
export async function openFilePreview(fileUrl: string): Promise<void> {
  if (!fileUrl) return;

  const requestPath = normalizePreviewUrl(fileUrl);
  const previewWindow = window.open('', '_blank');
  if (previewWindow) {
    previewWindow.opener = null;
  }

  try {
    const response = await http.preview(requestPath);
    const objectUrl = URL.createObjectURL(response.data);

    if (previewWindow) {
      previewWindow.location.href = objectUrl;
    } else {
      window.open(objectUrl, '_blank', 'noopener,noreferrer');
    }

    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
  } catch (error) {
    previewWindow?.close();
    throw error;
  }
}

function normalizePreviewUrl(fileUrl: string): string {
  const parsed = new URL(fileUrl, window.location.origin);
  if (parsed.origin !== window.location.origin) {
    throw new Error('不支持跨域文件预览');
  }

  const path = `${parsed.pathname}${parsed.search}`;
  if (path === '/api') return '/';
  return path.startsWith('/api/') ? path.slice(4) : path;
}
