import { useCallback, useEffect, useState } from 'react';
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
  Progress,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  App as AntdApp,
} from 'antd';
import {
  ApartmentOutlined,
  BarChartOutlined,
  CalendarOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { listDepts, type SysDept } from '@/api/system';
import { useUserStore } from '@/store/user';
import { exportDevelop } from '@/api/report';
import {
  addPlan,
  getPlan,
  getPlanProgress,
  pagePlans,
  removePlan,
  savePlanQuotas,
  updatePlan,
  PLAN_STATUS_COLORS,
  PLAN_STATUS_OPTIONS,
  type DevPlan,
  type PlanProgress,
  type QuotaProgress,
} from '@/api/plan';

const PAGE_SIZE = 10;

/** 年度下拉：今年向前推 6 年 */
const YEAR_OPTIONS = Array.from({ length: 6 }, (_, i) => {
  const y = dayjs().year() - i;
  return { label: `${y} 年`, value: y };
});

export default function DevelopPlanPage() {
  const { message, modal } = AntdApp.useApp();
  const can = useUserStore((s) => s.can);

  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<DevPlan[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNum, setPageNum] = useState(1);
  const [planYear, setPlanYear] = useState<number | undefined>(dayjs().year());
  const [orgId, setOrgId] = useState<number | undefined>();
  const [status, setStatus] = useState<number | undefined>();
  const [selected, setSelected] = useState<DevPlan | null>(null);
  const [orgs, setOrgs] = useState<SysDept[]>([]);

  const [progress, setProgress] = useState<PlanProgress | null>(null);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<DevPlan | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  // 指标分解
  const [quotaOpen, setQuotaOpen] = useState(false);
  const [quotaRows, setQuotaRows] = useState<
    { orgId: number; orgName?: string; quotaCount: number; remark?: string; reachedCount?: number; rate?: number }[]
  >([]);
  const [quotaSaving, setQuotaSaving] = useState(false);

  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      const res = await pagePlans({
        pageNum,
        pageSize: PAGE_SIZE,
        planYear,
        orgId,
        status,
      });
      setRows(res.records ?? []);
      setTotal(res.total ?? 0);
    } catch {
      setRows([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [pageNum, planYear, orgId, status]);

  useEffect(() => {
    fetchList();
  }, [fetchList]);

  useEffect(() => {
    listDepts()
      .then(setOrgs)
      .catch(() => setOrgs([]));
  }, []);

  // 顶部进度卡片跟随筛选的年度/组织
  useEffect(() => {
    getPlanProgress({ year: planYear, orgId })
      .then(setProgress)
      .catch(() => setProgress(null));
  }, [planYear, orgId]);

  const doSearch = () => {
    setPageNum(1);
    setSelected(null);
    fetchList();
  };

  const doReset = () => {
    setPlanYear(dayjs().year());
    setOrgId(undefined);
    setStatus(undefined);
    setPageNum(1);
    setSelected(null);
  };

  const openAdd = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ planYear: dayjs().year(), planCount: 0, status: 1 });
    setModalOpen(true);
  };

  const openEdit = (row: DevPlan) => {
    setEditing(row);
    form.setFieldsValue({
      ...row,
      issueDate: row.issueDate ? dayjs(row.issueDate) : undefined,
    });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      const payload = {
        ...values,
        planId: editing?.planId,
        issueDate: values.issueDate ? values.issueDate.format('YYYY-MM-DD') : undefined,
      };
      if (editing) {
        await updatePlan(payload);
        message.success('修改成功');
      } else {
        await addPlan(payload);
        message.success('下达成功');
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

  const confirmRemove = (row: DevPlan) => {
    modal.confirm({
      title: `确认删除 ${row.planYear} 年度计划？`,
      content: '删除后该计划及其指标分解将一并移除（仅草稿可删）。',
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await removePlan(row.planId);
        message.success('删除成功');
        if (selected?.planId === row.planId) setSelected(null);
        fetchList();
      },
    });
  };

  /** 打开指标分解 */
  const openQuota = async (row: DevPlan) => {
    try {
      const data = await getPlan(row.planId);
      setSelected(data);
      // 可分解的候选组织：本单位的下级组织
      const myPath = orgs.find((o) => o.orgId === data.orgId)?.orgPath;
      const children = orgs.filter((o) => {
        if (o.orgId === data.orgId) return false;
        if (!myPath) return true;
        return (o.orgPath ?? '').startsWith(myPath);
      });
      const existing = data.quotas ?? [];
      setQuotaRows(
        existing.length > 0
          ? existing.map((q) => ({
              orgId: q.orgId,
              orgName: q.orgName,
              quotaCount: q.quotaCount,
              remark: q.remark,
              reachedCount: q.reachedCount,
              rate: q.rate,
            }))
          : children.map((o) => ({
              orgId: o.orgId,
              orgName: o.orgName,
              quotaCount: 0,
            })),
      );
      setQuotaOpen(true);
    } catch {
      // 错误提示由拦截器统一处理
    }
  };

  const submitQuota = async () => {
    if (!selected) return;
    const items = quotaRows.filter((r) => r.quotaCount > 0);
    const sum = items.reduce((acc, r) => acc + r.quotaCount, 0);
    if (sum > (selected.planCount ?? 0)) {
      message.error(`指标合计 ${sum} 名，超过计划总数 ${selected.planCount ?? 0} 名`);
      return;
    }
    setQuotaSaving(true);
    try {
      await savePlanQuotas(
        selected.planId,
        items.map((r) => ({ orgId: r.orgId, quotaCount: r.quotaCount, remark: r.remark })),
      );
      message.success('指标已保存');
      setQuotaOpen(false);
      fetchList();
    } catch {
      // 错误提示由拦截器统一处理
    } finally {
      setQuotaSaving(false);
    }
  };

  const doExport = async () => {
    try {
      await exportDevelop({ orgId, status });
      message.success('导出已开始');
    } catch {
      // 错误提示由拦截器统一处理
    }
  };

  const quotaSum = quotaRows.reduce((acc, r) => acc + (r.quotaCount || 0), 0);

  return (
    <div>
      {/* 年度计划完成情况 */}
      <Row gutter={12} style={{ marginBottom: 12 }}>
        <Col xs={24} sm={6}>
          <Card variant="borderless">
            <Statistic
              title={`${progress?.planYear ?? dayjs().year()} 年计划发展数`}
              value={progress?.planCount ?? '—'}
              prefix={<CalendarOutlined style={{ color: '#C7000B' }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card variant="borderless">
            <Statistic
              title="已达发展对象人数"
              value={progress?.reachedCount ?? '—'}
              prefix={<TeamOutlined style={{ color: '#1890FF' }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card variant="borderless">
            <Statistic
              title="完成率"
              value={progress?.rate ?? '—'}
              suffix="%"
              prefix={<BarChartOutlined style={{ color: '#52C41A' }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card variant="borderless">
            <Statistic
              title="积极分子培养目标"
              value={progress?.activistTarget ?? '—'}
              prefix={<ApartmentOutlined style={{ color: '#FAAD14' }} />}
            />
          </Card>
        </Col>
      </Row>

      {progress && progress.planCount > 0 && (
        <Card variant="borderless" style={{ marginBottom: 12 }} styles={{ body: { padding: 16 } }}>
          <div style={{ marginBottom: 6 }}>
            {progress.orgName ?? '当前组织'} · {progress.planYear} 年度计划完成进度（
            {progress.reachedCount ?? 0} / {progress.planCount}）
          </div>
          <Progress percent={progress.rate ?? 0} status="active" />
        </Card>
      )}

      <Card variant="borderless" style={{ marginBottom: 12 }} styles={{ body: { padding: 16 } }}>
        <Space wrap>
          <Select
            style={{ width: 130 }}
            value={planYear}
            onChange={setPlanYear}
            options={YEAR_OPTIONS}
            placeholder="计划年度"
            allowClear
          />
          <Select
            placeholder="计划所属组织"
            allowClear
            showSearch
            optionFilterProp="label"
            style={{ width: 200 }}
            value={orgId}
            onChange={setOrgId}
            options={orgs.map((o) => ({ label: o.orgName, value: o.orgId }))}
          />
          <Select
            placeholder="状态"
            allowClear
            style={{ width: 130 }}
            value={status}
            onChange={setStatus}
            options={PLAN_STATUS_OPTIONS}
          />
          <Button type="primary" icon={<SearchOutlined />} onClick={doSearch}>
            查询
          </Button>
          <Button onClick={doReset}>重置</Button>
          <Button icon={<ReloadOutlined />} onClick={fetchList}>
            刷新
          </Button>
        </Space>
      </Card>

      <Card
        variant="borderless"
        title="发展党员年度计划"
        extra={
          <Space>
            {can('develop:plan:add') && (
              <Button type="primary" icon={<PlusOutlined />} onClick={openAdd}>
                下达计划
              </Button>
            )}
            {can('develop:plan:edit') && (
              <Button
                icon={<EditOutlined />}
                disabled={!selected}
                onClick={() => selected && openEdit(selected)}
              >
                编辑
              </Button>
            )}
            {can('develop:plan:edit') && (
              <Button
                icon={<ApartmentOutlined />}
                disabled={!selected}
                onClick={() => selected && openQuota(selected)}
              >
                指标分解
              </Button>
            )}
            {can('report:export') && (
              <Button icon={<DownloadOutlined />} onClick={doExport}>
                导出
              </Button>
            )}
            {can('develop:plan:remove') && (
              <Button
                icon={<DeleteOutlined />}
                disabled={!selected || selected.status !== 0}
                onClick={() => selected && confirmRemove(selected)}
              >
                删除
              </Button>
            )}
          </Space>
        }
        styles={{ body: { padding: 16 } }}
      >
        <Spin spinning={loading}>
          {rows.length === 0 && !loading ? (
            <Empty description="暂无年度计划" style={{ padding: '40px 0' }} />
          ) : (
            <Table
              rowKey="planId"
              dataSource={rows}
              size="middle"
              pagination={{
                current: pageNum,
                pageSize: PAGE_SIZE,
                total,
                showSizeChanger: false,
                showTotal: (t) => `共 ${t} 条`,
                onChange: (num) => {
                  setPageNum(num);
                  setSelected(null);
                },
              }}
              onRow={(record) => ({
                onClick: () => setSelected(record),
                style: {
                  cursor: 'pointer',
                  background: selected?.planId === record.planId ? '#FFF5F5' : undefined,
                },
              })}
              columns={[
                { title: '年度', dataIndex: 'planYear', width: 90 },
                { title: '计划所属组织', dataIndex: 'orgName', width: 200, ellipsis: true },
                { title: '计划发展数', dataIndex: 'planCount', width: 100 },
                { title: '积极分子目标', dataIndex: 'activistTarget', width: 110 },
                {
                  title: '状态',
                  dataIndex: 'status',
                  width: 100,
                  render: (v: number, r) => (
                    <Tag color={PLAN_STATUS_COLORS[v] ?? 'default'}>{r.statusLabel ?? '—'}</Tag>
                  ),
                },
                { title: '下达日期', dataIndex: 'issueDate', width: 120 },
                { title: '计划说明', dataIndex: 'description', ellipsis: true },
                {
                  title: '操作',
                  width: 160,
                  fixed: 'right',
                  render: (_, r) => (
                    <Space size={0}>
                      <Button
                        type="link"
                        size="small"
                        onClick={(e) => {
                          e.stopPropagation();
                          openQuota(r);
                        }}
                      >
                        指标
                      </Button>
                      {can('develop:plan:remove') && r.status === 0 && (
                        <Button
                          type="link"
                          size="small"
                          danger
                          onClick={(e) => {
                            e.stopPropagation();
                            confirmRemove(r);
                          }}
                        >
                          删除
                        </Button>
                      )}
                    </Space>
                  ),
                },
              ]}
            />
          )}
        </Spin>
      </Card>

      {/* 下达 / 编辑计划 */}
      <Modal
        open={modalOpen}
        title={editing ? '修改年度计划' : '下达年度计划'}
        onCancel={() => {
          setModalOpen(false);
          setEditing(null);
        }}
        onOk={submit}
        confirmLoading={submitting}
        okText="确定"
        cancelText="取消"
        width={680}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item
                name="planYear"
                label="计划年度"
                rules={[{ required: true, message: '请选择计划年度' }]}
              >
                <Select options={YEAR_OPTIONS} disabled={!!editing} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="orgId" label="计划所属组织">
                <Select
                  placeholder="默认当前所属党组织"
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  disabled={!!editing}
                  options={orgs.map((o) => ({ label: o.orgName, value: o.orgId }))}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="planCount"
                label="计划发展党员数"
                rules={[{ required: true, message: '请填写计划发展党员数' }]}
              >
                <InputNumber min={0} precision={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="activistTarget" label="入党积极分子培养目标数">
                <InputNumber min={0} precision={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="status" label="状态">
                <Select allowClear options={PLAN_STATUS_OPTIONS} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="issueDate" label="下达日期">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="计划说明">
            <Input.TextArea rows={3} placeholder="填写计划依据与要求" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 指标分解 */}
      <Modal
        open={quotaOpen}
        title={`指标分解${selected ? ` · ${selected.planYear} 年度（计划 ${selected.planCount} 名）` : ''}`}
        onCancel={() => setQuotaOpen(false)}
        onOk={submitQuota}
        confirmLoading={quotaSaving}
        okText="保存指标"
        cancelText="取消"
        width={820}
        destroyOnHidden
      >
        <div style={{ margin: '12px 0', color: quotaSum > (selected?.planCount ?? 0) ? '#CF1322' : '#666' }}>
          已分配合计 {quotaSum} / {selected?.planCount ?? 0} 名
          {quotaSum > (selected?.planCount ?? 0) ? '（超出计划总数）' : ''}
        </div>
        {quotaRows.length === 0 ? (
          <Empty description="没有可分配的下级组织" style={{ padding: '24px 0' }} />
        ) : (
          <Table
            rowKey="orgId"
            dataSource={quotaRows}
            size="small"
            pagination={false}
            columns={[
              { title: '下级组织', dataIndex: 'orgName', width: 200 },
              {
                title: '分配名额',
                width: 130,
                render: (_, r, index) => (
                  <InputNumber
                    min={0}
                    precision={0}
                    style={{ width: '100%' }}
                    value={r.quotaCount}
                    onChange={(v) =>
                      setQuotaRows((prev) => {
                        const next = [...prev];
                        next[index] = { ...next[index], quotaCount: v ?? 0 };
                        return next;
                      })
                    }
                  />
                ),
              },
              {
                title: '已完成',
                width: 110,
                render: (_, r) => (r.reachedCount == null ? '—' : `${r.reachedCount} 人`),
              },
              {
                title: '完成率',
                width: 140,
                render: (_, r) =>
                  r.rate == null ? '—' : <Progress percent={r.rate} size="small" />,
              },
              {
                title: '备注',
                render: (_, r, index) => (
                  <Input
                    value={r.remark}
                    onChange={(e) =>
                      setQuotaRows((prev) => {
                        const next = [...prev];
                        next[index] = { ...next[index], remark: e.target.value };
                        return next;
                      })
                    }
                  />
                ),
              },
            ]}
          />
        )}
      </Modal>
    </div>
  );
}
