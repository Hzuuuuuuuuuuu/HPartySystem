import { useEffect, useState } from 'react';
import { Button, Card, Empty, Space, Spin, Table, Tag, Upload, App as AntdApp } from 'antd';
import { ReloadOutlined, UploadOutlined } from '@ant-design/icons';
import { http } from '@/api/request';

interface AmTask {
  taskId: number;
  title: string;
  taskType: string;
  publishOrgId: number;
  publishOrgName?: string;
  activityName?: string;
  content?: string;
  startDate?: string;
  endDate?: string;
  deadline?: string;
  status?: number;
  publishBy?: string;
  publishTime?: string;
}

const TYPE_LABEL: Record<string, string> = {
  MEMBER_ASSEMBLY: '党员大会',
  BRANCH_COMMITTEE: '支部委员会',
  PARTY_GROUP: '党小组会',
  PARTY_LECTURE: '党课',
  THEME_PARTY_DAY: '主题党日',
  ORG_LIFE: '组织生活会',
};

const STATUS_META: Record<number, { text: string; color: string }> = {
  0: { text: '草稿', color: 'default' },
  1: { text: '已发布', color: 'processing' },
  2: { text: '已截止', color: 'warning' },
  3: { text: '已归档', color: 'default' },
};

/**
 * 活动任务通知（菜单「三会一课 > 活动任务通知」）。
 *
 * 与首页/会议页里的 TaskNoticeList 卡片视图不同，这里是完整的管理视图：
 * 表格列出全部任务，并提供「上传资料」入口。
 */
export default function MeetingTaskPage() {
  const { message } = AntdApp.useApp();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<AmTask[]>([]);

  const fetchList = async () => {
    setLoading(true);
    try {
      const res = await http.get<AmTask[]>('/party/task/list');
      setRows(res ?? []);
    } catch {
      setRows([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchList();
  }, []);

  const upload = async (taskId: number, file: File) => {
    const formData = new FormData();
    formData.append('taskId', String(taskId));
    formData.append('file', file);
    try {
      await http.upload('/party/task/submit', formData);
      message.success('资料上传成功');
      fetchList();
    } catch {
      // 拦截器已提示
    }
    return false;
  };

  return (
    <Card
      variant="borderless"
      title="活动任务通知"
      extra={
        <Button icon={<ReloadOutlined />} onClick={fetchList}>
          刷新
        </Button>
      }
    >
      <Spin spinning={loading}>
        {rows.length === 0 && !loading ? (
          <Empty description="暂无活动任务" style={{ padding: '40px 0' }} />
        ) : (
          <Table
            rowKey="taskId"
            dataSource={rows}
            size="middle"
            pagination={{ pageSize: 10, showSizeChanger: false }}
            columns={[
              {
                title: '活动类型',
                dataIndex: 'taskType',
                width: 120,
                render: (v: string) => <Tag color="red">{TYPE_LABEL[v] ?? v}</Tag>,
              },
              { title: '任务标题', dataIndex: 'title', ellipsis: true },
              { title: '活动名称', dataIndex: 'activityName', width: 130 },
              { title: '发布单位', dataIndex: 'publishOrgName', width: 200, ellipsis: true },
              {
                title: '活动时间',
                width: 200,
                render: (_, r) => `${r.startDate ?? '—'} ~ ${r.endDate ?? '—'}`,
              },
              { title: '发布人', dataIndex: 'publishBy', width: 100 },
              {
                title: '状态',
                dataIndex: 'status',
                width: 90,
                render: (v: number) => {
                  const s = STATUS_META[v] ?? STATUS_META[0];
                  return <Tag color={s.color}>{s.text}</Tag>;
                },
              },
              {
                title: '操作',
                width: 120,
                render: (_, r) => (
                  <Space>
                    <Upload showUploadList={false} beforeUpload={(f) => upload(r.taskId, f)}>
                      <Button type="link" size="small" icon={<UploadOutlined />}>
                        上传资料
                      </Button>
                    </Upload>
                  </Space>
                ),
              },
            ]}
          />
        )}
      </Spin>
    </Card>
  );
}
