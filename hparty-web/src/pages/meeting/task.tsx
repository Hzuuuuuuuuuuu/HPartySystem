import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Upload,
  App as AntdApp,
} from 'antd';
import {
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  ExportOutlined,
  PlusOutlined,
  ReloadOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  addTask,
  getTask,
  listTaskSubmits,
  listTasks,
  removeTask,
  submitTaskMaterial,
  updateTask,
  TASK_STATUS_COLORS,
  TASK_STATUS_OPTIONS,
  TASK_STATUS_TEXT,
  TASK_TYPE_OPTIONS,
  TASK_TYPE_TEXT,
  type AmTask,
  type AmTaskForm,
  type AmTaskSubmit,
} from '@/api/task';
import { useUserStore } from '@/store/user';
import { openFilePreview } from '@/utils/filePreview';

/**
 * 活动任务通知（菜单「三会一课 > 活动任务通知」）。
 *
 * 与会议页/组织生活页里的 TaskNoticeList 卡片视图不同，这里是完整的管理视图：
 * 发布 / 修改 / 删除 / 详情（含提交记录），以及支部侧的「上传资料」。
 * 四个写按钮分别按 task:add / task:edit / task:remove / task:submit 门控。
 */
export default function MeetingTaskPage() {
  const { message, modal } = AntdApp.useApp();
  const can = useUserStore((s) => s.can);

  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<AmTask[]>([]);
  /** 只存 ID，选中行从 rows 现取 —— 列表刷新后旧对象会变成过期副本，
   *  直接编辑预填会把改前的值又写回去 */
  const [selectedId, setSelectedId] = useState<number | null>(null);

  // 筛选项为空表示查全部（后端两个 query 参数均可空）
  const [taskType, setTaskType] = useState<string | undefined>();
  const [status, setStatus] = useState<number | undefined>();

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<AmTask | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  const [detailOpen, setDetailOpen] = useState(false);
  const [detail, setDetail] = useState<AmTask | null>(null);
  const [submits, setSubmits] = useState<AmTaskSubmit[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);

  const selected = rows.find((r) => r.taskId === selectedId) ?? null;

  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      setRows((await listTasks({ taskType, status })) ?? []);
    } catch {
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, [taskType, status]);

  useEffect(() => {
    fetchList();
  }, [fetchList]);

  /** 打开详情：任务详情与提交记录并发拉取 */
  const openDetail = async (taskId: number) => {
    setSelectedId(taskId);
    setDetailOpen(true);
    setDetailLoading(true);
    setDetail(null);
    setSubmits([]);
    try {
      const [task, records] = await Promise.all([getTask(taskId), listTaskSubmits(taskId)]);
      setDetail(task);
      setSubmits(records ?? []);
    } catch {
      // 错误提示由拦截器统一处理
    } finally {
      setDetailLoading(false);
    }
  };

  const openAdd = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (row: AmTask) => {
    setEditing(row);
    form.setFieldsValue({
      title: row.title,
      taskType: row.taskType,
      activityName: row.activityName,
      content: row.content,
      status: row.status ?? 1,
      startDate: row.startDate ? dayjs(row.startDate) : undefined,
      endDate: row.endDate ? dayjs(row.endDate) : undefined,
      deadline: row.deadline ? dayjs(row.deadline) : undefined,
    });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      // DatePicker 给的是 dayjs 对象，后端 LocalDate 用 @JsonFormat("yyyy-MM-dd")
      const toDate = (v?: dayjs.Dayjs) => (v ? v.format('YYYY-MM-DD') : undefined);
      const payload: AmTaskForm = {
        taskId: editing?.taskId,
        title: values.title,
        taskType: values.taskType,
        activityName: values.activityName,
        content: values.content,
        status: values.status,
        startDate: toDate(values.startDate),
        endDate: toDate(values.endDate),
        deadline: toDate(values.deadline),
      };
      if (editing) {
        await updateTask(payload);
        message.success('修改成功');
      } else {
        await addTask(payload);
        message.success('发布成功');
      }
      setModalOpen(false);
      setEditing(null);
      form.resetFields();
      fetchList();
    } catch {
      // 错误提示由拦截器统一处理
    } finally {
      setSaving(false);
    }
  };

  const confirmRemove = () => {
    if (!selected) return;
    modal.confirm({
      title: `确认删除「${selected.title}」？`,
      content: '删除后该任务及其已上传的提交记录将不再可见。',
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await removeTask(selected.taskId);
        message.success('删除成功');
        setSelectedId(null);
        fetchList();
      },
    });
  };

  const upload = async (taskId: number, file: File) => {
    try {
      await submitTaskMaterial(taskId, file);
      message.success('资料上传成功');
      fetchList();
      // 详情抽屉开着时同步刷新提交记录
      if (detailOpen && detail?.taskId === taskId) {
        setSubmits((await listTaskSubmits(taskId)) ?? []);
      }
    } catch {
      // 错误提示由拦截器统一处理
    }
    return false;
  };

  return (
    <div>
      <Card
        variant="borderless"
        title="活动任务通知"
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={fetchList}>
              刷新
            </Button>
            <Button
              icon={<EyeOutlined />}
              disabled={!selected}
              onClick={() => selected && openDetail(selected.taskId)}
            >
              详情
            </Button>
            {can('task:edit') && (
              <Button
                icon={<EditOutlined />}
                disabled={!selected}
                onClick={() => selected && openEdit(selected)}
              >
                编辑
              </Button>
            )}
            {can('task:remove') && (
              <Button icon={<DeleteOutlined />} disabled={!selected} onClick={confirmRemove}>
                删除
              </Button>
            )}
            {can('task:add') && (
              <Button type="primary" icon={<PlusOutlined />} onClick={openAdd}>
                发布任务
              </Button>
            )}
          </Space>
        }
      >
        <Space style={{ marginBottom: 12 }} wrap>
          <Select
            placeholder="活动类型"
            allowClear
            style={{ width: 160 }}
            options={TASK_TYPE_OPTIONS}
            value={taskType}
            onChange={(v: string | undefined) => setTaskType(v)}
          />
          <Select
            placeholder="状态"
            allowClear
            style={{ width: 140 }}
            options={TASK_STATUS_OPTIONS}
            value={status}
            onChange={(v: number | undefined) => setStatus(v)}
          />
        </Space>

        <Spin spinning={loading}>
          {rows.length === 0 && !loading ? (
            <Empty description="暂无活动任务" style={{ padding: '40px 0' }} />
          ) : (
            <Table
              rowKey="taskId"
              dataSource={rows}
              size="middle"
              pagination={{ pageSize: 10, showSizeChanger: false }}
              onRow={(record) => ({
                onClick: () => setSelectedId(record.taskId),
                style: {
                  cursor: 'pointer',
                  background: selectedId === record.taskId ? '#FFF5F5' : undefined,
                },
              })}
              columns={[
                {
                  title: '活动类型',
                  dataIndex: 'taskType',
                  width: 120,
                  render: (v: string) => <Tag color="red">{TASK_TYPE_TEXT[v] ?? v}</Tag>,
                },
                { title: '任务标题', dataIndex: 'title', ellipsis: true },
                { title: '活动名称', dataIndex: 'activityName', width: 130, ellipsis: true },
                { title: '发布单位', dataIndex: 'publishOrgName', width: 180, ellipsis: true },
                {
                  title: '活动时间',
                  width: 200,
                  render: (_, r) => `${r.startDate ?? '—'} ~ ${r.endDate ?? '—'}`,
                },
                { title: '材料截止', dataIndex: 'deadline', width: 110 },
                { title: '发布人', dataIndex: 'publishBy', width: 90 },
                {
                  title: '状态',
                  dataIndex: 'status',
                  width: 90,
                  render: (v: number) => (
                    <Tag color={TASK_STATUS_COLORS[v] ?? 'default'}>
                      {TASK_STATUS_TEXT[v] ?? '—'}
                    </Tag>
                  ),
                },
                {
                  title: '操作',
                  width: 110,
                  render: (_, r) =>
                    can('task:submit') && (
                      <Upload showUploadList={false} beforeUpload={(f) => upload(r.taskId, f)}>
                        <Button type="link" size="small" icon={<UploadOutlined />}>
                          上传资料
                        </Button>
                      </Upload>
                    ),
                },
              ]}
            />
          )}
        </Spin>
      </Card>

      {/* 发布 / 修改任务 */}
      <Modal
        open={modalOpen}
        title={editing ? '编辑活动任务' : '发布活动任务'}
        onCancel={() => {
          setModalOpen(false);
          setEditing(null);
        }}
        onOk={submit}
        confirmLoading={saving}
        okText="确定"
        cancelText="取消"
        width={720}
        destroyOnHidden
      >
        <Form
          form={form}
          layout="vertical"
          style={{ marginTop: 16 }}
          initialValues={{ status: 1 }}
        >
          <Form.Item
            name="title"
            label="任务标题"
            rules={[{ required: true, message: '请输入任务标题' }]}
          >
            <Input placeholder="如：2026年9月主题党日材料报送" />
          </Form.Item>

          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                name="taskType"
                label="活动类型"
                rules={[{ required: true, message: '请选择活动类型' }]}
              >
                <Select placeholder="请选择活动类型" options={TASK_TYPE_OPTIONS} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="activityName" label="活动名称">
                <Input placeholder="如：学党纪强党性主题党日" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="startDate" label="活动开始日期">
                <DatePicker style={{ width: '100%' }} placeholder="选择日期" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="endDate"
                label="活动结束日期"
                dependencies={['startDate']}
                rules={[
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      const start = getFieldValue('startDate');
                      if (value && start && value.isBefore(start, 'day')) {
                        return Promise.reject(new Error('结束日期不能早于开始日期'));
                      }
                      return Promise.resolve();
                    },
                  }),
                ]}
              >
                <DatePicker style={{ width: '100%' }} placeholder="选择日期" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="deadline" label="材料上传截止日期">
                <DatePicker style={{ width: '100%' }} placeholder="选择日期" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="status" label="状态">
                <Select placeholder="请选择状态" options={TASK_STATUS_OPTIONS} allowClear />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="content" label="活动内容">
            <Input.TextArea rows={4} placeholder="填写活动内容与材料报送要求" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 任务详情 + 提交记录 */}
      <Drawer
        open={detailOpen}
        title={detail ? `任务详情 · ${detail.title}` : '任务详情'}
        width={800}
        onClose={() => setDetailOpen(false)}
      >
        <Spin spinning={detailLoading}>
          {detail ? (
            <>
              <Descriptions size="small" column={2} bordered style={{ marginBottom: 16 }}>
                <Descriptions.Item label="活动类型">
                  <Tag color="red">{TASK_TYPE_TEXT[detail.taskType] ?? detail.taskType}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label="状态">
                  <Tag color={TASK_STATUS_COLORS[detail.status ?? 0] ?? 'default'}>
                    {TASK_STATUS_TEXT[detail.status ?? 0] ?? '—'}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="活动名称">
                  {detail.activityName ?? '—'}
                </Descriptions.Item>
                <Descriptions.Item label="发布单位">
                  {detail.publishOrgName ?? '—'}
                </Descriptions.Item>
                <Descriptions.Item label="活动时间">
                  {`${detail.startDate ?? '—'} ~ ${detail.endDate ?? '—'}`}
                </Descriptions.Item>
                <Descriptions.Item label="材料截止">{detail.deadline ?? '—'}</Descriptions.Item>
                <Descriptions.Item label="发布人">{detail.publishBy ?? '—'}</Descriptions.Item>
                <Descriptions.Item label="发布时间">{detail.publishTime ?? '—'}</Descriptions.Item>
                <Descriptions.Item label="活动内容" span={2}>
                  {detail.content ?? '—'}
                </Descriptions.Item>
              </Descriptions>

              <Card variant="borderless" size="small" title={`提交记录（${submits.length}）`}>
                {submits.length === 0 ? (
                  <Empty
                    description="该任务暂无支部提交材料"
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                  />
                ) : (
                  <Table
                    rowKey="submitId"
                    dataSource={submits}
                    size="small"
                    pagination={false}
                    columns={[
                      { title: '提交时间', dataIndex: 'submitTime', width: 170 },
                      { title: '提交人', dataIndex: 'submitBy', width: 100 },
                      { title: '备注', dataIndex: 'remark', ellipsis: true },
                      {
                        title: '材料',
                        width: 100,
                        render: (_, r) =>
                          r.fileUrl ? (
                            <Button
                              type="link"
                              size="small"
                              icon={<ExportOutlined />}
                              style={{ padding: 0 }}
                              onClick={() =>
                                void openFilePreview(r.fileUrl!).catch(() => undefined)
                              }
                            >
                              查看
                            </Button>
                          ) : (
                            '—'
                          ),
                      },
                    ]}
                  />
                )}
              </Card>
            </>
          ) : (
            !detailLoading && <Empty description="暂无数据" />
          )}
        </Spin>
      </Drawer>
    </div>
  );
}
