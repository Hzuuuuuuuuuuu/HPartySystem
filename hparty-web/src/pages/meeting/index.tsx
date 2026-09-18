import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  Modal,
  Row,
  Space,
  Spin,
  Table,
  Tag,
  DatePicker,
  App as AntdApp,
} from 'antd';
import { DownloadOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import FuncGrid, { type FuncItem } from '@/components/FuncGrid';
import TaskNoticeList from './TaskNoticeList';
import { http } from '@/api/request';
import { exportMeeting } from '@/api/report';
import { useUserStore } from '@/store/user';

/** 三会一课四类会议的入口配置，与图1 一致 */
const MEETING_ENTRIES: { type: string; slug: string; label: string; icon: string }[] = [
  { type: 'MEMBER_ASSEMBLY', slug: 'member-assembly', label: '党员大会', icon: 'UsergroupAddOutlined' },
  { type: 'BRANCH_COMMITTEE', slug: 'branch-committee', label: '支部委员会', icon: 'SolutionOutlined' },
  { type: 'PARTY_GROUP', slug: 'party-group', label: '党小组会', icon: 'ClusterOutlined' },
  { type: 'PARTY_LECTURE', slug: 'party-lecture', label: '党课', icon: 'BookOutlined' },
];

/**
 * URL 末段 → 会议类型。
 *
 * 三会一课下的 4 个菜单共用本页面组件，靠**路径**区分要展示哪一类会议。
 * 早期把筛选值塞在菜单表的 component 字段里（`meeting/index?type=...`），
 * 而动态路由是按组件文件名解析的，带查询串就找不到文件、路由不注册，
 * 4 个菜单全部落到 404。
 */
const PATH_TO_TYPE: Record<string, string> = Object.fromEntries(
  MEETING_ENTRIES.map((e) => [e.slug, e.type]),
);

const TYPE_LABEL: Record<string, string> = {
  MEMBER_ASSEMBLY: '党员大会',
  BRANCH_COMMITTEE: '支部委员会',
  PARTY_GROUP: '党小组会',
  PARTY_LECTURE: '党课',
  THEME_PARTY_DAY: '主题党日',
  ORG_LIFE: '组织生活会',
};

const STATUS_LABEL: Record<number, { text: string; color: string }> = {
  0: { text: '草稿', color: 'default' },
  1: { text: '待召开', color: 'processing' },
  2: { text: '进行中', color: 'processing' },
  3: { text: '已结束', color: 'success' },
  4: { text: '已归档', color: 'default' },
};

export default function MeetingPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { message } = AntdApp.useApp();
  const can = useUserStore((s) => s.can);

  // 会议类型由 URL 末段决定，例如 /meeting/member-assembly
  const activeType = PATH_TO_TYPE[location.pathname.split('/').pop() ?? ''] ?? '';

  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<any[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm();

  const fetchList = async () => {
    setLoading(true);
    try {
      const res = await http.get<any[]>('/party/meeting/list', {
        meetingType: activeType || undefined,
      });
      setRows(res ?? []);
    } catch {
      setRows([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchList();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeType]);

  const entries: FuncItem[] = MEETING_ENTRIES.map((e) => ({
    key: e.type,
    label: e.label,
    icon: e.icon,
    onClick: () => navigate(`/meeting/${e.slug}`),
  }));

  const submit = async () => {
    const values = await form.validateFields();
    try {
      await http.post('/party/meeting', {
        ...values,
        meetingType: activeType || 'MEMBER_ASSEMBLY',
        meetingDate: values.meetingDate?.format('YYYY-MM-DD'),
      });
      message.success('新增成功');
      setModalOpen(false);
      form.resetFields();
      fetchList();
    } catch {
      // 拦截器已提示
    }
  };

  return (
    <div>
      <div style={{ marginBottom: 12 }}>
        <FuncGrid items={entries} />
      </div>

      <Card
        variant="borderless"
        title={activeType ? `${TYPE_LABEL[activeType] ?? ''}列表` : '全部会议'}
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={fetchList}>
              刷新
            </Button>
            {can('report:export') && (
              <Button
                icon={<DownloadOutlined />}
                onClick={async () => {
                  try {
                    await exportMeeting({ meetingType: activeType || undefined });
                    message.success('导出已开始');
                  } catch {
                    // 错误提示由拦截器统一处理
                  }
                }}
              >
                导出开展情况
              </Button>
            )}
            {can('meeting:add') && (
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
                新增会议
              </Button>
            )}
          </Space>
        }
        styles={{ body: { padding: 16 } }}
        style={{ marginBottom: 12 }}
      >
        <Spin spinning={loading}>
          {rows.length === 0 && !loading ? (
            <Empty description="暂无会议记录" style={{ padding: '40px 0' }} />
          ) : (
            <Table
              rowKey="meetingId"
              dataSource={rows}
              pagination={{ pageSize: 10, showSizeChanger: false }}
              size="middle"
              columns={[
                {
                  title: '会议类型',
                  dataIndex: 'meetingType',
                  width: 120,
                  render: (v: string) => <Tag color="red">{TYPE_LABEL[v] ?? v}</Tag>,
                },
                { title: '会议标题', dataIndex: 'title', ellipsis: true },
                { title: '会议日期', dataIndex: 'meetingDate', width: 120 },
                { title: '地点', dataIndex: 'place', width: 160, ellipsis: true },
                { title: '主持人', dataIndex: 'hostName', width: 100 },
                {
                  title: '应到/实到',
                  width: 110,
                  render: (_, r: any) => `${r.shouldAttend ?? 0} / ${r.actualAttend ?? 0}`,
                },
                {
                  title: '状态',
                  dataIndex: 'status',
                  width: 100,
                  render: (v: number) => {
                    const s = STATUS_LABEL[v] ?? STATUS_LABEL[0];
                    return <Tag color={s.color}>{s.text}</Tag>;
                  },
                },
              ]}
            />
          )}
        </Spin>
      </Card>

      {/* 活动任务通知 —— 还原图1 底部区域 */}
      <TaskNoticeList />

      <Modal
        open={modalOpen}
        title={`新增${TYPE_LABEL[activeType] ?? '会议'}`}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        okText="确定"
        cancelText="取消"
        destroyOnHidden
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="title" label="会议标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input placeholder="如：2026年9月党员大会" />
          </Form.Item>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="meetingDate" label="会议日期">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="place" label="会议地点">
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="hostName" label="主持人">
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="shouldAttend" label="应到人数">
                <Input type="number" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="content" label="会议内容">
            <Input.TextArea rows={4} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
