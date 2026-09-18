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
  Tooltip,
  App as AntdApp,
} from 'antd';
import {
  AuditOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  SolutionOutlined,
  TeamOutlined,
  UserSwitchOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { listDepts, type SysDept } from '@/api/system';
import { useUserStore } from '@/store/user';
import { exportReview } from '@/api/report';
import {
  addReview,
  getReview,
  getReviewStatistics,
  pageReviews,
  removeReview,
  startReview,
  submitOrgEval,
  submitPeerEval,
  submitSelfEval,
  updateReview,
  DISPOSE_OPTIONS,
  REVIEW_GRADE_COLORS,
  REVIEW_GRADE_OPTIONS,
  REVIEW_STATUS_COLORS,
  REVIEW_STATUS_OPTIONS,
  type ReviewBatch,
  type ReviewDetail,
  type ReviewStatistics,
} from '@/api/review';

const PAGE_SIZE = 10;

/** 年度下拉：今年向前推 6 年 */
const YEAR_OPTIONS = Array.from({ length: 6 }, (_, i) => {
  const y = dayjs().year() - i;
  return { label: `${y} 年`, value: y };
});

export default function ReviewPage() {
  const { message, modal } = AntdApp.useApp();
  const can = useUserStore((s) => s.can);
  const currentPersonId = useUserStore((s) => s.userInfo?.personId);

  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<ReviewBatch[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNum, setPageNum] = useState(1);
  const [reviewYear, setReviewYear] = useState<number | undefined>(dayjs().year());
  const [orgId, setOrgId] = useState<number | undefined>();
  const [status, setStatus] = useState<number | undefined>();
  const [keyword, setKeyword] = useState('');
  const [selected, setSelected] = useState<ReviewBatch | null>(null);
  const [orgs, setOrgs] = useState<SysDept[]>([]);

  const [stat, setStat] = useState<ReviewStatistics | null>(null);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ReviewBatch | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  // 详情抽屉
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [current, setCurrent] = useState<ReviewBatch | null>(null);
  const [details, setDetails] = useState<ReviewDetail[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);

  // 自评 / 互评
  const [selfOpen, setSelfOpen] = useState(false);
  const [selfForm] = Form.useForm();
  const [peerOpen, setPeerOpen] = useState(false);
  const [peerRows, setPeerRows] = useState<{ personId: number; personName?: string; score?: number }[]>([]);
  const [peerSaving, setPeerSaving] = useState(false);

  // 组织评定
  const [orgOpen, setOrgOpen] = useState(false);
  const [orgRows, setOrgRows] = useState<
    { detailId: number; personName?: string; massScore?: number; orgScore?: number; grade?: number; orgComment?: string; dispose?: string }[]
  >([]);
  const [orgSaving, setOrgSaving] = useState(false);

  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      const res = await pageReviews({
        pageNum,
        pageSize: PAGE_SIZE,
        reviewYear,
        orgId,
        status,
        keyword: keyword || undefined,
      });
      setRows(res.records ?? []);
      setTotal(res.total ?? 0);
    } catch {
      setRows([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [pageNum, reviewYear, orgId, status, keyword]);

  useEffect(() => {
    fetchList();
  }, [fetchList]);

  useEffect(() => {
    listDepts()
      .then(setOrgs)
      .catch(() => setOrgs([]));
  }, []);

  const loadStat = useCallback((reviewId?: number) => {
    getReviewStatistics(reviewId)
      .then(setStat)
      .catch(() => setStat(null));
  }, []);

  useEffect(() => {
    loadStat(selected?.reviewId);
  }, [selected, loadStat]);

  const doSearch = () => {
    setPageNum(1);
    setSelected(null);
    fetchList();
  };

  const doReset = () => {
    setReviewYear(dayjs().year());
    setOrgId(undefined);
    setStatus(undefined);
    setKeyword('');
    setPageNum(1);
    setSelected(null);
  };

  const openAdd = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ reviewYear: dayjs().year() });
    setModalOpen(true);
  };

  const openEdit = (row: ReviewBatch) => {
    setEditing(row);
    form.setFieldsValue({
      ...row,
      startDate: row.startDate ? dayjs(row.startDate) : undefined,
      endDate: row.endDate ? dayjs(row.endDate) : undefined,
    });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      const payload = {
        ...values,
        reviewId: editing?.reviewId,
        startDate: values.startDate ? values.startDate.format('YYYY-MM-DD') : undefined,
        endDate: values.endDate ? values.endDate.format('YYYY-MM-DD') : undefined,
      };
      if (editing) {
        await updateReview(payload);
        message.success('修改成功');
      } else {
        await addReview(payload);
        message.success('创建成功');
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

  const confirmRemove = (row?: ReviewBatch | null) => {
    const target = row ?? selected;
    if (!target) return;
    modal.confirm({
      title: `确认删除「${target.title}」？`,
      content: '删除后该批次及其评议明细将一并移除（仅草稿可删）。',
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await removeReview(target.reviewId);
        message.success('删除成功');
        if (selected?.reviewId === target.reviewId) setSelected(null);
        fetchList();
      },
    });
  };

  /** 启动评议：生成明细 */
  const confirmStart = (row: ReviewBatch) => {
    modal.confirm({
      title: `启动「${row.title}」？`,
      content: '将按该组织下全部党员生成评议明细，批次进入「自评中」。',
      okText: '确认启动',
      cancelText: '取消',
      onOk: async () => {
        const count = await startReview(row.reviewId);
        message.success(`已启动，生成 ${count} 条评议明细`);
        fetchList();
        if (selected?.reviewId === row.reviewId) openDetail(row.reviewId);
      },
    });
  };

  const openDetail = async (reviewId: number) => {
    setDrawerOpen(true);
    setDetailLoading(true);
    try {
      const data = await getReview(reviewId);
      setCurrent(data);
      setDetails(data.details ?? []);
    } catch {
      setDetails([]);
    } finally {
      setDetailLoading(false);
    }
  };

  /** 我的自评入口 */
  const openSelf = () => {
    if (!current) return;
    if (currentPersonId == null) {
      message.warning('当前账号未关联人员档案，无法参与民主评议');
      return;
    }
    const mine = details.find((d) => d.personId === currentPersonId);
    if (!mine) {
      message.warning('您不在本次民主评议名单中');
      return;
    }
    if (current.status == null || current.status < 1 || current.status >= 4) {
      message.warning(`当前状态为「${current.statusLabel ?? ''}」，已不能再提交自评`);
      return;
    }
    selfForm.resetFields();
    selfForm.setFieldsValue({ selfScore: mine.selfScore, selfComment: mine.selfComment });
    setSelfOpen(true);
  };

  const submitSelf = async () => {
    if (!current) return;
    const values = await selfForm.validateFields();
    try {
      await submitSelfEval(current.reviewId, values);
      message.success('自评已提交');
      setSelfOpen(false);
      openDetail(current.reviewId);
      fetchList();
    } catch {
      // 错误提示由拦截器统一处理
    }
  };

  /** 我的互评入口：名单里排除自己 */
  const openPeer = () => {
    if (!current) return;
    if (currentPersonId == null) {
      message.warning('当前账号未关联人员档案，无法参与互评');
      return;
    }
    if (current.status == null || current.status < 1 || current.status >= 4) {
      message.warning(`当前状态为「${current.statusLabel ?? ''}」，已不能再提交互评`);
      return;
    }
    const others = details.filter((d) => d.personId !== currentPersonId);
    if (others.length === 0) {
      message.warning('没有可评议的其他党员');
      return;
    }
    setPeerRows(others.map((d) => ({ personId: d.personId, personName: d.personName })));
    setPeerOpen(true);
  };

  const submitPeer = async () => {
    if (!current) return;
    const items = peerRows.filter((r) => r.score !== undefined && r.score !== null);
    if (items.length === 0) {
      message.warning('请至少为一名同志打分');
      return;
    }
    setPeerSaving(true);
    try {
      const count = await submitPeerEval(
        current.reviewId,
        items.map((r) => ({ personId: r.personId, score: r.score as number })),
      );
      message.success(`互评已提交 ${count} 条`);
      setPeerOpen(false);
      openDetail(current.reviewId);
      fetchList();
    } catch {
      // 「不能给自己打分」等提示由拦截器统一处理
    } finally {
      setPeerSaving(false);
    }
  };

  /** 组织评定入口 */
  const openOrg = () => {
    if (!current) return;
    if (current.status == null || current.status < 1) {
      message.warning('评议尚未启动，无法组织评定');
      return;
    }
    setOrgRows(
      details.map((d) => ({
        detailId: d.detailId,
        personName: d.personName,
        massScore: d.massScore,
        orgScore: d.orgScore,
        grade: d.grade,
        orgComment: d.orgComment,
        dispose: d.dispose,
      })),
    );
    setOrgOpen(true);
  };

  const submitOrg = async () => {
    if (!current) return;
    const items = orgRows
      .filter((r) => r.orgScore !== undefined && r.orgScore !== null)
      .map((r) => ({ ...r, orgScore: r.orgScore as number }));
    if (items.length === 0) {
      message.warning('请至少为一名党员填写组织评定得分');
      return;
    }
    setOrgSaving(true);
    try {
      const count = await submitOrgEval(current.reviewId, items);
      message.success(`组织评定已提交 ${count} 条`);
      setOrgOpen(false);
      openDetail(current.reviewId);
      fetchList();
    } catch {
      // 优秀名额超额等提示由拦截器统一处理
    } finally {
      setOrgSaving(false);
    }
  };

  const doExport = async () => {
    try {
      await exportReview({
        reviewYear,
        orgId,
        reviewId: selected?.reviewId,
        status: undefined,
      });
      message.success('导出已开始');
    } catch {
      // 错误提示由拦截器统一处理
    }
  };

  const gradeColorOf = (grade?: number) =>
    grade == null ? 'default' : (REVIEW_GRADE_COLORS[grade] ?? 'default');

  const p = current?.progress;

  return (
    <div>
      {/* 统计卡片 */}
      <Row gutter={12} style={{ marginBottom: 12 }}>
        <Col xs={24} sm={6}>
          <Card variant="borderless">
            <Statistic
              title="评议总人数"
              value={stat?.total ?? '—'}
              prefix={<TeamOutlined style={{ color: '#C7000B' }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card variant="borderless">
            <Statistic
              title="已定等次"
              value={stat?.graded ?? '—'}
              prefix={<SolutionOutlined style={{ color: '#1890FF' }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card variant="borderless">
            <Statistic
              title="优秀名额"
              value={stat?.excellentQuota ?? '—'}
              prefix={<AuditOutlined style={{ color: '#FAAD14' }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={6}>
          <Card variant="borderless">
            <Statistic
              title="已评优秀"
              value={stat?.progress?.excellentUsed ?? '—'}
              prefix={<AuditOutlined style={{ color: '#52C41A' }} />}
            />
          </Card>
        </Col>
      </Row>

      <Card variant="borderless" style={{ marginBottom: 12 }} styles={{ body: { padding: 16 } }}>
        <Space wrap>
          <Select
            style={{ width: 130 }}
            value={reviewYear}
            onChange={setReviewYear}
            options={YEAR_OPTIONS}
            placeholder="评议年度"
            allowClear
          />
          <Select
            placeholder="党组织"
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
            style={{ width: 140 }}
            value={status}
            onChange={setStatus}
            options={REVIEW_STATUS_OPTIONS}
          />
          <Input
            placeholder="搜索批次标题"
            allowClear
            style={{ width: 200 }}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={doSearch}
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
        title="民主评议党员批次"
        extra={
          <Space>
            {can('review:add') && (
              <Button type="primary" icon={<PlusOutlined />} onClick={openAdd}>
                新增批次
              </Button>
            )}
            {can('review:edit') && (
              <Button
                icon={<EditOutlined />}
                disabled={!selected}
                onClick={() => selected && openEdit(selected)}
              >
                编辑
              </Button>
            )}
            {can('review:edit') && (
              <Button
                icon={<PlayCircleOutlined />}
                disabled={!selected || selected.status !== 0}
                onClick={() => selected && confirmStart(selected)}
              >
                启动评议
              </Button>
            )}
            <Button
              icon={<SolutionOutlined />}
              disabled={!selected}
              onClick={() => selected && openDetail(selected.reviewId)}
            >
              评议明细
            </Button>
            {can('report:export') && (
              <Button icon={<DownloadOutlined />} onClick={doExport}>
                导出
              </Button>
            )}
            {can('review:remove') && (
              <Button
                icon={<DeleteOutlined />}
                disabled={!selected || selected.status !== 0}
                onClick={() => confirmRemove()}
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
            <Empty description="暂无评议批次" style={{ padding: '40px 0' }} />
          ) : (
            <Table
              rowKey="reviewId"
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
                onDoubleClick: () => openDetail(record.reviewId),
                style: {
                  cursor: 'pointer',
                  background: selected?.reviewId === record.reviewId ? '#FFF5F5' : undefined,
                },
              })}
              columns={[
                { title: '批次标题', dataIndex: 'title', ellipsis: true },
                { title: '年度', dataIndex: 'reviewYear', width: 80 },
                { title: '党组织', dataIndex: 'orgName', width: 150, ellipsis: true },
                {
                  title: '状态',
                  dataIndex: 'status',
                  width: 110,
                  render: (v: number, r) => (
                    <Tag color={REVIEW_STATUS_COLORS[v] ?? 'default'}>{r.statusLabel ?? '—'}</Tag>
                  ),
                },
                {
                  title: '完成进度（自评/互评/组织评定）',
                  width: 260,
                  render: (_, r) => {
                    const g = r.progress;
                    if (!g) return '—';
                    return (
                      <Space size={4}>
                        <Tooltip title={`自评 ${g.selfDone}/${g.total}`}>
                          <Progress
                            percent={g.selfRate}
                            size="small"
                            style={{ width: 70 }}
                            format={(v) => `${v}%`}
                          />
                        </Tooltip>
                        <Tooltip title={`互评 ${g.peerDone}/${g.total}`}>
                          <Progress
                            percent={g.peerRate}
                            size="small"
                            style={{ width: 70 }}
                            format={(v) => `${v}%`}
                          />
                        </Tooltip>
                        <Tooltip title={`组织评定 ${g.orgDone}/${g.total}`}>
                          <Progress
                            percent={g.orgRate}
                            size="small"
                            style={{ width: 70 }}
                            format={(v) => `${v}%`}
                          />
                        </Tooltip>
                      </Space>
                    );
                  },
                },
                { title: '优秀名额', dataIndex: 'excellentQuota', width: 90 },
                {
                  title: '起止日期',
                  width: 190,
                  render: (_, r) => `${r.startDate ?? '—'} ~ ${r.endDate ?? '—'}`,
                },
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
                          openDetail(r.reviewId);
                        }}
                      >
                        明细
                      </Button>
                      {can('review:edit') && r.status === 0 && (
                        <Button
                          type="link"
                          size="small"
                          onClick={(e) => {
                            e.stopPropagation();
                            confirmStart(r);
                          }}
                        >
                          启动
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

      {/* 新增 / 编辑批次 */}
      <Modal
        open={modalOpen}
        title={editing ? '编辑评议批次' : '新增评议批次'}
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
            label="批次标题"
            rules={[{ required: true, message: '请输入批次标题' }]}
          >
            <Input placeholder="如：2026年度民主评议党员" />
          </Form.Item>
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item
                name="reviewYear"
                label="评议年度"
                rules={[{ required: true, message: '请选择评议年度' }]}
              >
                <Select options={YEAR_OPTIONS} disabled={!!editing} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="orgId" label="党组织">
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
              <Form.Item name="excellentQuota" label="优秀名额">
                <InputNumber
                  min={0}
                  precision={0}
                  style={{ width: '100%' }}
                  placeholder="留空按 30% 自动推算"
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="startDate" label="自评开始日期">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="endDate" label="评议截止日期">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="评议说明">
            <Input.TextArea rows={3} placeholder="填写评议安排与要求" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 批次详情 */}
      <Drawer
        open={drawerOpen}
        title={`评议明细${current ? ` · ${current.title}` : ''}`}
        width={1000}
        onClose={() => setDrawerOpen(false)}
        extra={
          current && (
            <Space>
              <Button icon={<UserSwitchOutlined />} onClick={openSelf}>
                我的自评
              </Button>
              <Button icon={<TeamOutlined />} onClick={openPeer}>
                我的互评
              </Button>
              {can('review:judge') && (
                <Button type="primary" icon={<AuditOutlined />} onClick={openOrg}>
                  组织评定
                </Button>
              )}
            </Space>
          )
        }
      >
        <Spin spinning={detailLoading}>
          {current && (
            <>
              <Descriptions size="small" column={3} bordered style={{ marginBottom: 12 }}>
                <Descriptions.Item label="党组织">{current.orgName ?? '—'}</Descriptions.Item>
                <Descriptions.Item label="年度">{current.reviewYear}</Descriptions.Item>
                <Descriptions.Item label="状态">
                  <Tag color={REVIEW_STATUS_COLORS[current.status ?? 0] ?? 'default'}>
                    {current.statusLabel ?? '—'}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="优秀名额">
                  {current.excellentQuota ?? '—'}
                </Descriptions.Item>
                <Descriptions.Item label="起止日期">
                  {`${current.startDate ?? '—'} ~ ${current.endDate ?? '—'}`}
                </Descriptions.Item>
                <Descriptions.Item label="已评优秀">
                  {p?.excellentUsed ?? 0}
                </Descriptions.Item>
              </Descriptions>

              <Row gutter={12} style={{ marginBottom: 12 }}>
                <Col span={8}>
                  <Card size="small">
                    <div style={{ marginBottom: 4 }}>自评完成率</div>
                    <Progress percent={p?.selfRate ?? 0} status="active" />
                    <div style={{ color: '#999', fontSize: 12 }}>
                      {p?.selfDone ?? 0} / {p?.total ?? 0} 人
                    </div>
                  </Card>
                </Col>
                <Col span={8}>
                  <Card size="small">
                    <div style={{ marginBottom: 4 }}>互评完成率</div>
                    <Progress percent={p?.peerRate ?? 0} status="active" strokeColor="#1890FF" />
                    <div style={{ color: '#999', fontSize: 12 }}>
                      {p?.peerDone ?? 0} / {p?.total ?? 0} 人
                    </div>
                  </Card>
                </Col>
                <Col span={8}>
                  <Card size="small">
                    <div style={{ marginBottom: 4 }}>组织评定完成率</div>
                    <Progress percent={p?.orgRate ?? 0} status="active" strokeColor="#52C41A" />
                    <div style={{ color: '#999', fontSize: 12 }}>
                      {p?.orgDone ?? 0} / {p?.total ?? 0} 人
                    </div>
                  </Card>
                </Col>
              </Row>

              {details.length === 0 ? (
                <Empty description="尚未生成评议明细，请先「启动评议」" style={{ padding: '40px 0' }} />
              ) : (
                <Table
                  rowKey="detailId"
                  dataSource={details}
                  size="middle"
                  pagination={{ pageSize: 20, showSizeChanger: false }}
                  columns={[
                    { title: '姓名', dataIndex: 'personName', width: 100 },
                    {
                      title: '自评',
                      dataIndex: 'selfScore',
                      width: 80,
                      render: (v?: number) => v ?? '—',
                    },
                    {
                      title: '互评均分',
                      dataIndex: 'peerScore',
                      width: 90,
                      render: (v: number | undefined, r: ReviewDetail) =>
                        v == null ? '—' : `${v}${r.peerCount ? `（${r.peerCount}人）` : ''}`,
                    },
                    {
                      title: '群众评议',
                      dataIndex: 'massScore',
                      width: 90,
                      render: (v?: number) => v ?? '—',
                    },
                    {
                      title: '组织评定',
                      dataIndex: 'orgScore',
                      width: 90,
                      render: (v?: number) => v ?? '—',
                    },
                    {
                      title: '综合得分',
                      dataIndex: 'totalScore',
                      width: 90,
                      render: (v?: number) => (v == null ? '—' : <b>{v}</b>),
                    },
                    {
                      title: '等次',
                      dataIndex: 'grade',
                      width: 90,
                      render: (v?: number) =>
                        v == null ? (
                          '—'
                        ) : (
                          <Tag color={gradeColorOf(v)}>
                            {REVIEW_GRADE_OPTIONS.find((o) => o.value === v)?.label ?? v}
                          </Tag>
                        ),
                    },
                    { title: '组织评定意见', dataIndex: 'orgComment', ellipsis: true },
                    { title: '处置意见', dataIndex: 'dispose', width: 110 },
                  ]}
                />
              )}
            </>
          )}
        </Spin>
      </Drawer>

      {/* 我的自评 */}
      <Modal
        open={selfOpen}
        title="提交自评"
        onCancel={() => setSelfOpen(false)}
        onOk={submitSelf}
        okText="提交"
        cancelText="取消"
        destroyOnHidden
      >
        <Form form={selfForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="selfScore"
            label="自评得分（0-100）"
            rules={[{ required: true, message: '请填写自评得分' }]}
          >
            <InputNumber min={0} max={100} precision={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="selfComment" label="自评意见">
            <Input.TextArea rows={4} placeholder="对照党员标准进行自我评价" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 我的互评 */}
      <Modal
        open={peerOpen}
        title="提交互评（不能给自己打分）"
        onCancel={() => setPeerOpen(false)}
        onOk={submitPeer}
        confirmLoading={peerSaving}
        okText="提交"
        cancelText="取消"
        width={640}
        destroyOnHidden
      >
        <Table
          rowKey="personId"
          dataSource={peerRows}
          size="small"
          pagination={false}
          style={{ marginTop: 12 }}
          columns={[
            { title: '姓名', dataIndex: 'personName', width: 140 },
            {
              title: '得分（0-100）',
              render: (_, r, index) => (
                <InputNumber
                  min={0}
                  max={100}
                  precision={1}
                  style={{ width: 140 }}
                  value={r.score}
                  onChange={(v) =>
                    setPeerRows((prev) => {
                      const next = [...prev];
                      next[index] = { ...next[index], score: v ?? undefined };
                      return next;
                    })
                  }
                />
              ),
            },
          ]}
        />
      </Modal>

      {/* 组织评定 */}
      <Modal
        open={orgOpen}
        title="组织评定（优秀比例不超过党员总数的 30%）"
        onCancel={() => setOrgOpen(false)}
        onOk={submitOrg}
        confirmLoading={orgSaving}
        okText="提交"
        cancelText="取消"
        width={1000}
        destroyOnHidden
      >
        <Table
          rowKey="detailId"
          dataSource={orgRows}
          size="small"
          pagination={{ pageSize: 8, showSizeChanger: false }}
          style={{ marginTop: 12 }}
          columns={[
            { title: '姓名', dataIndex: 'personName', width: 90 },
            {
              title: '群众评议',
              width: 110,
              render: (_, r, index) => (
                <InputNumber
                  min={0}
                  max={100}
                  precision={1}
                  style={{ width: '100%' }}
                  value={r.massScore}
                  onChange={(v) =>
                    setOrgRows((prev) => {
                      const next = [...prev];
                      next[index] = { ...next[index], massScore: v ?? undefined };
                      return next;
                    })
                  }
                />
              ),
            },
            {
              title: '组织评定',
              width: 110,
              render: (_, r, index) => (
                <InputNumber
                  min={0}
                  max={100}
                  precision={1}
                  style={{ width: '100%' }}
                  value={r.orgScore}
                  onChange={(v) =>
                    setOrgRows((prev) => {
                      const next = [...prev];
                      next[index] = { ...next[index], orgScore: v ?? undefined };
                      return next;
                    })
                  }
                />
              ),
            },
            {
              title: '等次',
              width: 120,
              render: (_, r, index) => (
                <Select
                  allowClear
                  style={{ width: '100%' }}
                  placeholder="按分数自动"
                  value={r.grade}
                  options={REVIEW_GRADE_OPTIONS}
                  onChange={(v) =>
                    setOrgRows((prev) => {
                      const next = [...prev];
                      next[index] = { ...next[index], grade: v ?? undefined };
                      return next;
                    })
                  }
                />
              ),
            },
            {
              title: '组织评定意见',
              render: (_, r, index) => (
                <Input
                  value={r.orgComment}
                  onChange={(e) =>
                    setOrgRows((prev) => {
                      const next = [...prev];
                      next[index] = { ...next[index], orgComment: e.target.value };
                      return next;
                    })
                  }
                />
              ),
            },
            {
              title: '处置意见',
              width: 130,
              render: (_, r, index) => (
                <Select
                  allowClear
                  style={{ width: '100%' }}
                  placeholder="不合格时填"
                  value={r.dispose}
                  options={DISPOSE_OPTIONS}
                  onChange={(v) =>
                    setOrgRows((prev) => {
                      const next = [...prev];
                      next[index] = { ...next[index], dispose: v ?? undefined };
                      return next;
                    })
                  }
                />
              ),
            },
          ]}
        />
      </Modal>
    </div>
  );
}
