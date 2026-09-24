import { useEffect, useMemo, useState } from 'react';
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
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import FuncGrid, { type FuncItem } from '@/components/FuncGrid';
import { useUserStore } from '@/store/user';
import {
  addThemePartyDay,
  listThemePartyDays,
  removeThemePartyDay,
  updateThemePartyDay,
  MEETING_STATUS_COLORS,
  MEETING_STATUS_OPTIONS,
  MEETING_STATUS_TEXT,
  type ThemePartyDay,
} from '@/api/partyday';

/** 顶部四个功能入口 */
const ENTRIES: { key: string; label: string; icon: string }[] = [
  { key: 'PLAN', label: '主题党日安排', icon: 'ScheduleOutlined' },
  { key: 'RECORD', label: '活动记录', icon: 'ProfileOutlined' },
  { key: 'MATERIAL', label: '学习材料', icon: 'BookOutlined' },
  { key: 'VOLUNTEER', label: '志愿服务', icon: 'HeartOutlined' },
];

const VIEW_TITLE: Record<string, string> = {
  PLAN: '主题党日安排',
  RECORD: '主题党日活动记录',
  MATERIAL: '学习材料',
  VOLUNTEER: '志愿服务',
};

export default function PartyDayPage() {
  const { message, modal } = AntdApp.useApp();
  const can = useUserStore((s) => s.can);

  const [view, setView] = useState('PLAN');
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<ThemePartyDay[]>([]);
  const [selected, setSelected] = useState<ThemePartyDay | null>(null);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ThemePartyDay | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const fetchList = async () => {
    setLoading(true);
    try {
      setRows((await listThemePartyDays()) ?? []);
    } catch {
      setRows([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchList();
  }, []);

  /** 「安排」只看未开始的，「记录」看全部 */
  const dataSource = useMemo(() => {
    if (view === 'PLAN') {
      return rows.filter((r) => r.status === 0 || r.status === 1);
    }
    return rows;
  }, [rows, view]);

  const entries: FuncItem[] = ENTRIES.map((e) => ({
    key: e.key,
    label: e.label,
    icon: e.icon,
    onClick: () => {
      setView(e.key);
      setSelected(null);
    },
  }));

  const openAdd = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (row: ThemePartyDay) => {
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
        meetingDate: values.meetingDate ? values.meetingDate.format('YYYY-MM-DD') : undefined,
      };
      if (editing) {
        await updateThemePartyDay(payload);
        message.success('修改成功');
      } else {
        await addThemePartyDay(payload);
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
      content: '删除后该主题党日记录将不再展示。',
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await removeThemePartyDay(selected.meetingId);
        message.success('删除成功');
        setSelected(null);
        fetchList();
      },
    });
  };

  return (
    <div>
      <div style={{ marginBottom: 12 }}>
        <FuncGrid items={entries} />
      </div>

      <Card
        variant="borderless"
        title={VIEW_TITLE[view]}
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={fetchList}>
              刷新
            </Button>
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
                新增主题党日
              </Button>
            )}
          </Space>
        }
        styles={{ body: { padding: 16 } }}
      >
        {view === 'MATERIAL' || view === 'VOLUNTEER' ? (
          <Empty
            description={
              view === 'MATERIAL'
                ? '主题党日学习材料请在「组织生活」模块的分类入口中查看'
                : '志愿服务活动记录请在「党员服务」模块中查看'
            }
            style={{ padding: '60px 0' }}
          />
        ) : (
          <Spin spinning={loading}>
            {dataSource.length === 0 && !loading ? (
              <Empty description="暂无主题党日记录" style={{ padding: '40px 0' }} />
            ) : (
              <Table
                rowKey="meetingId"
                dataSource={dataSource}
                size="middle"
                pagination={{ pageSize: 10, showSizeChanger: false }}
                onRow={(record) => ({
                  onClick: () => setSelected(record),
                  style: {
                    cursor: 'pointer',
                    background:
                      selected?.meetingId === record.meetingId ? '#FFF5F5' : undefined,
                  },
                })}
                columns={[
                  { title: '活动主题', dataIndex: 'title', ellipsis: true },
                  { title: '党组织', dataIndex: 'orgName', width: 160, ellipsis: true },
                  { title: '活动日期', dataIndex: 'meetingDate', width: 120 },
                  {
                    title: '时间',
                    width: 130,
                    render: (_, r) =>
                      r.startTime || r.endTime ? `${r.startTime ?? ''}~${r.endTime ?? ''}` : '—',
                  },
                  { title: '活动地点', dataIndex: 'place', width: 160, ellipsis: true },
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
        )}
      </Card>

      <Modal
        open={modalOpen}
        title={editing ? '编辑主题党日' : '新增主题党日'}
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
            label="活动主题"
            rules={[{ required: true, message: '请输入活动主题' }]}
          >
            <Input placeholder="如：传承红色基因 砥砺初心使命" />
          </Form.Item>

          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="meetingDate" label="活动日期">
                <DatePicker style={{ width: '100%' }} placeholder="选择日期" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="place" label="活动地点">
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

          <Form.Item name="content" label="活动内容">
            <Input.TextArea rows={4} placeholder="填写活动开展情况" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
