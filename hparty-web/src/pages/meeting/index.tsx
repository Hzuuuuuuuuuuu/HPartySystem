import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  Button,
  Card,
  Col,
  DatePicker,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  App as AntdApp,
} from 'antd';
import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import FuncGrid, { type FuncItem } from '@/components/FuncGrid';
import TaskNoticeList from './TaskNoticeList';
import { exportMeeting } from '@/api/report';
import {
  addMeeting,
  listMeetings,
  removeMeeting,
  updateMeeting,
  MEETING_STATUS_COLORS,
  MEETING_STATUS_OPTIONS,
  MEETING_STATUS_TEXT,
  type MeetingRecord,
} from '@/api/meeting';
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

export default function MeetingPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { message, modal } = AntdApp.useApp();
  const can = useUserStore((s) => s.can);

  // 会议类型由 URL 末段决定，例如 /meeting/member-assembly
  const activeType = PATH_TO_TYPE[location.pathname.split('/').pop() ?? ''] ?? '';
  const typeLabel = TYPE_LABEL[activeType] ?? '会议';

  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<MeetingRecord[]>([]);
  const [selected, setSelected] = useState<MeetingRecord | null>(null);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<MeetingRecord | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const fetchList = async () => {
    setLoading(true);
    try {
      setRows((await listMeetings(activeType || undefined)) ?? []);
    } catch {
      setRows([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // 切换菜单（URL 变化）时换一批数据，同时清掉上一类的选中行
    setSelected(null);
    fetchList();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeType]);

  const entries: FuncItem[] = MEETING_ENTRIES.map((e) => ({
    key: e.type,
    label: e.label,
    icon: e.icon,
    onClick: () => navigate(`/meeting/${e.slug}`),
  }));

  const openAdd = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (row: MeetingRecord) => {
    setEditing(row);
    form.setFieldsValue({
      ...row,
      meetingDate: row.meetingDate ? dayjs(row.meetingDate) : undefined,
    });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      const payload = {
        ...values,
        meetingId: editing?.meetingId,
        meetingType: activeType || 'MEMBER_ASSEMBLY',
        meetingDate: values.meetingDate ? values.meetingDate.format('YYYY-MM-DD') : undefined,
      };
      if (editing) {
        await updateMeeting(payload);
        message.success('修改成功');
      } else {
        await addMeeting(payload);
        message.success('新增成功');
      }
      setModalOpen(false);
      setEditing(null);
      form.resetFields();
      fetchList();
    } catch {
      // 错误提示由拦截器统一处理
    } finally {
      setSubmitting(false);
    }
  };

  const confirmRemove = () => {
    if (!selected) return;
    modal.confirm({
      title: `确认删除「${selected.title}」？`,
      content: '删除后该会议记录及其签到名单将一并移除。',
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await removeMeeting(selected.meetingId);
        message.success('删除成功');
        setSelected(null);
        fetchList();
      },
    });
  };

  const doExport = async () => {
    try {
      await exportMeeting({ meetingType: activeType || undefined });
      message.success('导出已开始');
    } catch {
      // 错误提示由拦截器统一处理
    }
  };

  return (
    <div>
      <div style={{ marginBottom: 12 }}>
        <FuncGrid items={entries} />
      </div>

      <Card
        variant="borderless"
        title={activeType ? `${typeLabel}列表` : '全部会议'}
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={fetchList}>
              刷新
            </Button>
            {can('report:export') && (
              <Button icon={<DownloadOutlined />} onClick={doExport}>
                导出开展情况
              </Button>
            )}
            {can('meeting:edit') && (
              <Button
                icon={<EditOutlined />}
                disabled={!selected}
                onClick={() => selected && openEdit(selected)}
              >
                编辑
              </Button>
            )}
            {can('meeting:remove') && (
              <Button icon={<DeleteOutlined />} disabled={!selected} onClick={confirmRemove}>
                删除
              </Button>
            )}
            {can('meeting:add') && (
              <Button type="primary" icon={<PlusOutlined />} onClick={openAdd}>
                新增{typeLabel}
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
              onRow={(record) => ({
                onClick: () => setSelected(record),
                style: {
                  cursor: 'pointer',
                  background: selected?.meetingId === record.meetingId ? '#FFF5F5' : undefined,
                },
              })}
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
                  render: (_, r) => `${r.shouldAttend ?? 0} / ${r.actualAttend ?? 0}`,
                },
                {
                  title: '状态',
                  dataIndex: 'status',
                  width: 100,
                  render: (v: number, r) => (
                    <Tag color={MEETING_STATUS_COLORS[v] ?? 'default'}>
                      {r.statusLabel ?? MEETING_STATUS_TEXT[v] ?? '—'}
                    </Tag>
                  ),
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
        title={editing ? `编辑${typeLabel}` : `新增${typeLabel}`}
        onCancel={() => {
          setModalOpen(false);
          setEditing(null);
        }}
        onOk={submit}
        confirmLoading={submitting}
        okText="确定"
        cancelText="取消"
        width={720}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="title"
            label="会议标题"
            rules={[{ required: true, message: '请输入会议标题' }]}
          >
            <Input placeholder="如：2026年9月党员大会" />
          </Form.Item>

          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="meetingDate" label="会议日期">
                <DatePicker style={{ width: '100%' }} placeholder="选择日期" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="place" label="会议地点">
                <Input placeholder="如：支部活动室" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="startTime" label="开始时间">
                <Input placeholder="如：09:00" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="endTime" label="结束时间">
                <Input placeholder="如：11:00" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="hostName" label="主持人">
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="recorderName" label="记录人">
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="shouldAttend"
                label="应到人数"
                rules={[{ required: true, message: '请填写应到人数' }]}
              >
                <InputNumber min={1} precision={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="actualAttend"
                label="实到人数"
                dependencies={['shouldAttend']}
                rules={[
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      const should = getFieldValue('shouldAttend');
                      if (value != null && should != null && Number(value) > Number(should)) {
                        return Promise.reject(
                          new Error(`实到人数不能大于应到人数（${should}）`),
                        );
                      }
                      return Promise.resolve();
                    },
                  }),
                ]}
              >
                <InputNumber min={0} precision={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="status" label="状态">
                <Select placeholder="请选择状态" options={MEETING_STATUS_OPTIONS} allowClear />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="content" label="会议内容">
            <Input.TextArea rows={4} placeholder="填写会议开展情况" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
