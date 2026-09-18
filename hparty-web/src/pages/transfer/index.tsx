import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
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
  Row,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Timeline,
  App as AntdApp,
} from 'antd';
import {
  AuditOutlined,
  DeleteOutlined,
  EditOutlined,
  ExportOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { listDepts, listPersonOptions, type PersonOption, type SysDept } from '@/api/system';
import { useUserStore } from '@/store/user';
import { openFilePreview } from '@/utils/filePreview';
import {
  acceptTransfer,
  addTransfer,
  getTransfer,
  issueTransfer,
  listOverdueTransfers,
  pageTransfers,
  rejectTransfer,
  removeTransfer,
  revokeTransfer,
  updateTransfer,
  TRANSFER_STATUS_COLORS,
  TRANSFER_STATUS_OPTIONS,
  TRANSFER_TYPE_OPTIONS,
  type PartyTransfer,
} from '@/api/transfer';

const PAGE_SIZE = 10;

/**
 * 组织关系转接。
 *
 * <p>列表按状态显示操作按钮：待提交可改/可开具/可撤销/可删除；已开具与已超期可接收/拒绝/撤销。
 * 详情用 Drawer 展示流转时间线与介绍信信息。超期清单是列表的一个筛选视图（status=4 与
 * 已开具但过期的单子）。</p>
 */
export default function TransferPage() {
  const { message, modal } = AntdApp.useApp();
  const can = useUserStore((s) => s.can);

  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<PartyTransfer[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNum, setPageNum] = useState(1);

  const [personName, setPersonName] = useState('');
  const [transferNo, setTransferNo] = useState('');
  const [transferType, setTransferType] = useState<number | undefined>();
  const [status, setStatus] = useState<number | undefined>();
  const [range, setRange] = useState<[dayjs.Dayjs | null, dayjs.Dayjs | null] | null>(null);
  const [overdueOnly, setOverdueOnly] = useState(false);

  const [orgs, setOrgs] = useState<SysDept[]>([]);
  const [persons, setPersons] = useState<PersonOption[]>([]);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<PartyTransfer | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const [rejectTarget, setRejectTarget] = useState<PartyTransfer | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const [rejecting, setRejecting] = useState(false);

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [detail, setDetail] = useState<PartyTransfer | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      if (overdueOnly) {
        // 超期清单走后端专用接口（status=1 且已过期，或已被标记为 4）
        const list = await listOverdueTransfers({
          personName: personName || undefined,
          beginDate: range?.[0] ? range[0].format('YYYY-MM-DD') : undefined,
          endDate: range?.[1] ? range[1].format('YYYY-MM-DD') : undefined,
        });
        setRows(list ?? []);
        setTotal(list?.length ?? 0);
      } else {
        const res = await pageTransfers({
          pageNum,
          pageSize: PAGE_SIZE,
          personName: personName || undefined,
          transferNo: transferNo || undefined,
          transferType,
          status,
          beginDate: range?.[0] ? range[0].format('YYYY-MM-DD') : undefined,
          endDate: range?.[1] ? range[1].format('YYYY-MM-DD') : undefined,
        });
        setRows(res.records ?? []);
        setTotal(res.total ?? 0);
      }
    } catch {
      setRows([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [pageNum, personName, transferNo, transferType, status, range, overdueOnly]);

  useEffect(() => {
    fetchList();
  }, [fetchList]);

  useEffect(() => {
    listDepts()
      .then(setOrgs)
      .catch(() => setOrgs([]));
    listPersonOptions()
      .then(setPersons)
      .catch(() => setPersons([]));
  }, []);

  const doSearch = () => {
    setPageNum(1);
    fetchList();
  };

  const doReset = () => {
    setPersonName('');
    setTransferNo('');
    setTransferType(undefined);
    setStatus(undefined);
    setRange(null);
    setOverdueOnly(false);
    setPageNum(1);
  };

  const openAdd = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ transferType: 1, validDays: 90 });
    setModalOpen(true);
  };

  const openEdit = (row: PartyTransfer) => {
    setEditing(row);
    form.resetFields();
    form.setFieldsValue({
      transferType: row.transferType,
      personId: row.personId,
      fromOrgId: row.fromOrgId,
      toOrgId: row.toOrgId,
      reason: row.reason,
      validDays: row.validDays ?? 90,
      remark: row.remark,
    });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      if (editing) {
        await updateTransfer({ transferId: editing.transferId, ...values });
        message.success('修改成功');
      } else {
        await addTransfer(values);
        message.success('发起成功');
      }
      setModalOpen(false);
      setEditing(null);
      form.resetFields();
      fetchList();
    } catch {
      // 错误提示由 axios 拦截器统一处理
    } finally {
      setSubmitting(false);
    }
  };

  const openDetail = async (row: PartyTransfer) => {
    setDrawerOpen(true);
    setDetailLoading(true);
    try {
      setDetail(await getTransfer(row.transferId));
    } catch {
      setDetail(null);
    } finally {
      setDetailLoading(false);
    }
  };

  /** 开具介绍信 */
  const doIssue = (row: PartyTransfer) => {
    modal.confirm({
      title: `确认为「${row.personName}」开具介绍信？`,
      content: `介绍信有效期 ${row.validDays ?? 90} 天。开具后组织关系暂不变更，需目标党组织接收后才落地。`,
      okText: '确认开具',
      cancelText: '取消',
      onOk: async () => {
        await issueTransfer(row.transferId);
        message.success('介绍信已开具');
        fetchList();
      },
    });
  };

  /** 接收 —— 组织关系真正变更的一步 */
  const doAccept = (row: PartyTransfer) => {
    modal.confirm({
      title: `确认接收「${row.personName}」的组织关系？`,
      content: `接收后该同志的组织关系将转入「${row.toOrgName ?? '目标组织'}」。若其有进行中的发展党员流程，流程会一并迁入且不会被重置（培养教育时间连续计算）。`,
      okText: '确认接收',
      cancelText: '取消',
      onOk: async () => {
        await acceptTransfer(row.transferId);
        message.success('接收成功');
        fetchList();
      },
    });
  };

  /** 拒绝 */
  const doReject = async () => {
    if (!rejectTarget) return;
    if (!rejectReason.trim()) {
      message.warning('请填写拒绝原因');
      return;
    }
    setRejecting(true);
    try {
      await rejectTransfer(rejectTarget.transferId, rejectReason.trim());
      message.success('已拒绝');
      setRejectTarget(null);
      setRejectReason('');
      fetchList();
    } catch {
      // 错误提示由 axios 拦截器统一处理
    } finally {
      setRejecting(false);
    }
  };

  /** 撤销 */
  const doRevoke = (row: PartyTransfer) => {
    modal.confirm({
      title: `确认撤销「${row.personName}」的转接单？`,
      content: '撤销后组织关系保持不变，介绍信作废。',
      okText: '确认撤销',
      cancelText: '取消',
      onOk: async () => {
        await revokeTransfer(row.transferId);
        message.success('已撤销');
        fetchList();
      },
    });
  };

  /** 删除 */
  const doRemove = (row: PartyTransfer) => {
    modal.confirm({
      title: `确认删除转接单「${row.transferNo}」？`,
      content: '仅「待提交」状态的转接单可以删除。',
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await removeTransfer(row.transferId);
        message.success('删除成功');
        fetchList();
      },
    });
  };

  return (
    <div>
      <Card variant="borderless" style={{ marginBottom: 12 }} styles={{ body: { padding: 16 } }}>
        <Space wrap>
          <Input
            placeholder="搜索姓名"
            allowClear
            style={{ width: 160 }}
            value={personName}
            onChange={(e) => setPersonName(e.target.value)}
            onPressEnter={doSearch}
          />
          <Input
            placeholder="转接单号 / 介绍信号"
            allowClear
            style={{ width: 190 }}
            value={transferNo}
            onChange={(e) => setTransferNo(e.target.value)}
            onPressEnter={doSearch}
          />
          <Select
            placeholder="类型"
            allowClear
            style={{ width: 130 }}
            value={transferType}
            onChange={setTransferType}
            options={TRANSFER_TYPE_OPTIONS}
          />
          <Select
            placeholder="状态"
            allowClear
            style={{ width: 130 }}
            value={status}
            onChange={setStatus}
            options={TRANSFER_STATUS_OPTIONS}
          />
          <DatePicker.RangePicker
            placeholder={['开具日期起', '开具日期止']}
            value={range}
            onChange={(v) => setRange(v as [dayjs.Dayjs | null, dayjs.Dayjs | null] | null)}
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
        title={overdueOnly ? '超期未落地清单（口袋党员）' : '组织关系转接单'}
        extra={
          <Space>
            <Button
              icon={<WarningOutlined />}
              type={overdueOnly ? 'primary' : 'default'}
              danger={overdueOnly}
              onClick={() => {
                setOverdueOnly((v) => !v);
                setPageNum(1);
              }}
            >
              {overdueOnly ? '返回全部' : '超期清单'}
            </Button>
            {can('transfer:add') && (
              <Button type="primary" icon={<PlusOutlined />} onClick={openAdd}>
                发起转接
              </Button>
            )}
          </Space>
        }
        styles={{ body: { padding: 16 } }}
      >
        {overdueOnly && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message="以下转接单的介绍信已过有效期但尚未落地，相关人员已构成「口袋党员」，请及时督促接收。"
          />
        )}
        <Spin spinning={loading}>
          {rows.length === 0 && !loading ? (
            <Empty description="暂无转接数据" style={{ padding: '40px 0' }} />
          ) : (
            <Table
              rowKey="transferId"
              dataSource={rows}
              size="middle"
              pagination={
                overdueOnly
                  ? false
                  : {
                      current: pageNum,
                      pageSize: PAGE_SIZE,
                      total,
                      showSizeChanger: false,
                      showTotal: (t) => `共 ${t} 条`,
                      onChange: setPageNum,
                    }
              }
              columns={[
                { title: '转接单号', dataIndex: 'transferNo', width: 140 },
                { title: '姓名', dataIndex: 'personName', width: 90 },
                {
                  title: '类型',
                  dataIndex: 'transferType',
                  width: 100,
                  render: (v: number, r) => (
                    <Tag color="red">{r.transferTypeLabel ?? TRANSFER_TYPE_OPTIONS.find((o) => o.value === v)?.label ?? '—'}</Tag>
                  ),
                },
                { title: '原党组织', dataIndex: 'fromOrgName', width: 130, ellipsis: true },
                { title: '目标党组织', dataIndex: 'toOrgName', width: 130, ellipsis: true },
                {
                  title: '介绍信',
                  dataIndex: 'letterNo',
                  width: 160,
                  render: (v: string, r) =>
                    v ? (
                      <div style={{ lineHeight: 1.4 }}>
                        <div>{v}</div>
                        <div style={{ fontSize: 12, color: r.overdue ? '#FF4D4F' : '#8C8C8C' }}>
                          有效期至 {r.expireDate ?? '—'}
                        </div>
                      </div>
                    ) : (
                      '—'
                    ),
                },
                {
                  title: '状态',
                  dataIndex: 'status',
                  width: 110,
                  render: (v: number, r) => (
                    <Space size={4}>
                      <Tag color={TRANSFER_STATUS_COLORS[v] ?? 'default'}>
                        {r.statusLabel ?? TRANSFER_STATUS_OPTIONS.find((o) => o.value === v)?.label ?? '—'}
                      </Tag>
                      {r.overdue && <Tag color="error">超期 {r.overdueDays ?? 0} 天</Tag>}
                    </Space>
                  ),
                },
                { title: '开具日期', dataIndex: 'letterDate', width: 110 },
                {
                  title: '操作',
                  width: 230,
                  fixed: 'right',
                  render: (_, r) => (
                    <Space size={0} wrap>
                      <Button type="link" size="small" onClick={() => openDetail(r)}>
                        详情
                      </Button>
                      {can('transfer:edit') && r.status === 0 && (
                        <Button type="link" size="small" onClick={() => openEdit(r)}>
                          编辑
                        </Button>
                      )}
                      {can('transfer:handle') && r.status === 0 && (
                        <Button type="link" size="small" onClick={() => doIssue(r)}>
                          开具介绍信
                        </Button>
                      )}
                      {can('transfer:handle') && (r.status === 1 || r.status === 4) && (
                        <>
                          <Button type="link" size="small" onClick={() => doAccept(r)}>
                            接收
                          </Button>
                          <Button
                            type="link"
                            size="small"
                            danger
                            onClick={() => {
                              setRejectTarget(r);
                              setRejectReason('');
                            }}
                          >
                            拒绝
                          </Button>
                        </>
                      )}
                      {can('transfer:handle') && (r.status === 0 || r.status === 1 || r.status === 4) && (
                        <Button type="link" size="small" onClick={() => doRevoke(r)}>
                          撤销
                        </Button>
                      )}
                      {can('transfer:remove') && r.status === 0 && (
                        <Button type="link" size="small" danger onClick={() => doRemove(r)}>
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

      {/* 发起 / 编辑 */}
      <Modal
        open={modalOpen}
        title={editing ? `编辑转接单 ${editing.transferNo ?? ''}` : '发起组织关系转接'}
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
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                name="personId"
                label="转接人员"
                rules={[{ required: true, message: '请选择转接人员' }]}
              >
                <Select
                  placeholder="请选择人员"
                  showSearch
                  optionFilterProp="label"
                  disabled={!!editing}
                  options={persons.map((p) => ({
                    label: `${p.name}${p.orgName ? `（${p.orgName}）` : ''}`,
                    value: p.personId,
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="transferType"
                label="转接类型"
                rules={[{ required: true, message: '请选择转接类型' }]}
              >
                <Select placeholder="请选择" disabled={!!editing} options={TRANSFER_TYPE_OPTIONS} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="toOrgId"
                label="目标党组织"
                rules={[{ required: true, message: '请选择目标党组织' }]}
              >
                <Select
                  placeholder="请选择目标党组织"
                  showSearch
                  optionFilterProp="label"
                  options={orgs.map((o) => ({ label: o.orgName, value: o.orgId }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="validDays"
                label="介绍信有效期（天）"
                tooltip="开具介绍信时按此天数推算失效日期，默认 90 天"
              >
                <InputNumber min={1} precision={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="reason" label="转接事由">
            <Input.TextArea rows={2} placeholder="如：因工作单位变动，申请转移组织关系" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 拒绝原因 */}
      <Modal
        open={!!rejectTarget}
        title={`拒绝接收「${rejectTarget?.personName ?? ''}」的组织关系`}
        onCancel={() => setRejectTarget(null)}
        onOk={doReject}
        confirmLoading={rejecting}
        okText="确认拒绝"
        okButtonProps={{ danger: true }}
        cancelText="取消"
        destroyOnHidden
      >
        <Input.TextArea
          rows={3}
          style={{ marginTop: 16 }}
          placeholder="请填写拒绝原因，如：材料不齐、介绍信已过期等"
          value={rejectReason}
          onChange={(e) => setRejectReason(e.target.value)}
        />
      </Modal>

      {/* 详情 */}
      <Drawer
        open={drawerOpen}
        title={detail ? `转接详情 · ${detail.transferNo ?? ''}` : '转接详情'}
        width={720}
        onClose={() => setDrawerOpen(false)}
      >
        <Spin spinning={detailLoading}>
          {detail ? (
            <>
              <Descriptions column={2} size="small" bordered>
                <Descriptions.Item label="姓名">{detail.personName ?? '—'}</Descriptions.Item>
                <Descriptions.Item label="类型">
                  {detail.transferTypeLabel ?? '—'}
                </Descriptions.Item>
                <Descriptions.Item label="原党组织">
                  {detail.fromOrgName ?? '—'}
                </Descriptions.Item>
                <Descriptions.Item label="目标党组织">
                  {detail.toOrgName ?? '—'}
                </Descriptions.Item>
                <Descriptions.Item label="介绍信号">
                  {detail.letterNo ?? '—'}
                </Descriptions.Item>
                <Descriptions.Item label="有效期">
                  {detail.letterDate
                    ? `${detail.letterDate} 至 ${detail.expireDate ?? '—'}（${detail.validDays ?? 0} 天）`
                    : '尚未开具'}
                </Descriptions.Item>
                <Descriptions.Item label="状态">
                  <Space size={4}>
                    <Tag color={TRANSFER_STATUS_COLORS[detail.status ?? 0] ?? 'default'}>
                      {detail.statusLabel ?? '—'}
                    </Tag>
                    {detail.overdue && <Tag color="error">超期 {detail.overdueDays ?? 0} 天</Tag>}
                  </Space>
                </Descriptions.Item>
                <Descriptions.Item label="转接完成日期">
                  {detail.transferDate ?? '—'}
                </Descriptions.Item>
                <Descriptions.Item label="经办人">
                  {detail.handlerName ?? '—'}
                </Descriptions.Item>
                <Descriptions.Item label="事由" span={2}>
                  {detail.reason ?? '—'}
                </Descriptions.Item>
                {detail.rejectReason && (
                  <Descriptions.Item label="拒绝原因" span={2}>
                    {detail.rejectReason}
                  </Descriptions.Item>
                )}
              </Descriptions>

              <div style={{ margin: '20px 0 8px', fontWeight: 600 }}>流转时间线</div>
              {(detail.timeline?.length ?? 0) === 0 ? (
                <Empty description="暂无流转记录" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : (
                <Timeline
                  items={(detail.timeline ?? []).map((node) => ({
                    color:
                      node.title === '接收'
                        ? 'green'
                        : node.title === '拒绝' || node.title === '超期'
                          ? 'red'
                          : 'blue',
                    children: (
                      <div>
                        <Space size={8}>
                          <span style={{ fontWeight: 500 }}>{node.title}</span>
                          <Tag>{node.status}</Tag>
                          {node.time && (
                            <span style={{ fontSize: 12, color: '#8C8C8C' }}>{node.time}</span>
                          )}
                        </Space>
                        <div style={{ fontSize: 12, color: '#595959', marginTop: 2 }}>
                          {node.operator ? `${node.operator} · ` : ''}
                          {node.description}
                        </div>
                      </div>
                    ),
                  }))}
                />
              )}

              {detail.fileUrl && (
                <Button
                  type="link"
                  icon={<ExportOutlined />}
                  onClick={() => void openFilePreview(detail.fileUrl!).catch(() => undefined)}
                  style={{ paddingLeft: 0 }}
                >
                  查看介绍信扫描件
                </Button>
              )}
            </>
          ) : (
            <Empty description="暂无数据" />
          )}
        </Spin>
      </Drawer>
    </div>
  );
}
