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
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tabs,
  Tag,
  App as AntdApp,
} from 'antd';
import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  FileAddOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { listDepts, listPersonOptions, type PersonOption, type SysDept } from '@/api/system';
import { exportDues } from '@/api/report';
import { useUserStore } from '@/store/user';
import {
  addDuesRecord,
  addDuesUse,
  generateDuesRecords,
  getDuesStatistics,
  listDuesUses,
  pageDuesRecords,
  removeDuesRecord,
  removeDuesUse,
  updateDuesRecord,
  DUES_PAY_TYPE_OPTIONS,
  DUES_STATUS_COLORS,
  DUES_STATUS_OPTIONS,
  DUES_USE_CATEGORY_OPTIONS,
  MONTH_OPTIONS,
  type DuesRecord,
  type DuesStatistics,
  type DuesUse,
} from '@/api/dues';

const PAGE_SIZE = 10;

/** 年度下拉：今年向前推 6 年 */
const YEAR_OPTIONS = Array.from({ length: 6 }, (_, i) => {
  const y = dayjs().year() - i;
  return { label: `${y} 年`, value: y };
});

export default function DuesPage() {
  const { message, modal } = AntdApp.useApp();
  const can = useUserStore((s) => s.can);

  const [activeTab, setActiveTab] = useState('record');
  const [orgs, setOrgs] = useState<SysDept[]>([]);
  const [persons, setPersons] = useState<PersonOption[]>([]);

  // ---------- 收缴记录 ----------
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<DuesRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNum, setPageNum] = useState(1);
  const [orgId, setOrgId] = useState<number | undefined>();
  const [duesYear, setDuesYear] = useState<number | undefined>();
  const [duesMonth, setDuesMonth] = useState<number | undefined>();
  const [status, setStatus] = useState<number | undefined>();
  const [personName, setPersonName] = useState('');
  const [selected, setSelected] = useState<DuesRecord | null>(null);

  const [stat, setStat] = useState<DuesStatistics | null>(null);
  const [statYear, setStatYear] = useState(dayjs().year());

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<DuesRecord | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const [genOpen, setGenOpen] = useState(false);
  const [genYear, setGenYear] = useState(dayjs().year());
  const [genMonth, setGenMonth] = useState(dayjs().month() + 1);
  const [generating, setGenerating] = useState(false);

  // ---------- 使用记录 ----------
  const [useLoading, setUseLoading] = useState(false);
  const [useRows, setUseRows] = useState<DuesUse[]>([]);
  const [useOrgId, setUseOrgId] = useState<number | undefined>();
  const [useYear, setUseYear] = useState<number | undefined>();
  const [useSelected, setUseSelected] = useState<DuesUse | null>(null);
  const [useModalOpen, setUseModalOpen] = useState(false);
  const [useSubmitting, setUseSubmitting] = useState(false);
  const [useForm] = Form.useForm();

  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      const res = await pageDuesRecords({
        pageNum,
        pageSize: PAGE_SIZE,
        orgId,
        duesYear,
        duesMonth,
        status,
        personName: personName || undefined,
      });
      setRows(res.records ?? []);
      setTotal(res.total ?? 0);
    } catch {
      setRows([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [pageNum, orgId, duesYear, duesMonth, status, personName]);

  const fetchStat = useCallback(async () => {
    try {
      setStat(await getDuesStatistics(statYear));
    } catch {
      setStat(null);
    }
  }, [statYear]);

  const fetchUses = useCallback(async () => {
    setUseLoading(true);
    try {
      setUseRows((await listDuesUses({ orgId: useOrgId, useYear })) ?? []);
    } catch {
      setUseRows([]);
    } finally {
      setUseLoading(false);
    }
  }, [useOrgId, useYear]);

  useEffect(() => {
    fetchList();
  }, [fetchList]);

  useEffect(() => {
    fetchStat();
  }, [fetchStat]);

  useEffect(() => {
    if (activeTab === 'use') fetchUses();
  }, [activeTab, fetchUses]);

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
    setSelected(null);
    fetchList();
  };

  const doReset = () => {
    setOrgId(undefined);
    setDuesYear(undefined);
    setDuesMonth(undefined);
    setStatus(undefined);
    setPersonName('');
    setPageNum(1);
    setSelected(null);
  };

  // ---------- 收缴记录操作 ----------

  const openAdd = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({
      duesYear: dayjs().year(),
      duesMonth: dayjs().month() + 1,
      status: 0,
    });
    setModalOpen(true);
  };

  const openEdit = (row: DuesRecord) => {
    setEditing(row);
    form.setFieldsValue({
      ...row,
      payDate: row.payDate ? dayjs(row.payDate) : undefined,
    });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      const payload = {
        ...values,
        duesId: editing?.duesId,
        payDate: values.payDate ? values.payDate.format('YYYY-MM-DD') : undefined,
      };
      if (editing) {
        await updateDuesRecord(payload);
        message.success('修改成功');
      } else {
        await addDuesRecord(payload);
        message.success('新增成功');
      }
      setModalOpen(false);
      setEditing(null);
      form.resetFields();
      fetchList();
      fetchStat();
    } catch {
      // 错误提示由拦截器统一处理
    } finally {
      setSubmitting(false);
    }
  };

  const confirmRemove = () => {
    if (!selected) return;
    modal.confirm({
      title: `确认删除「${selected.personName ?? ''}」${selected.duesYear ?? ''}年${selected.duesMonth ?? ''}月的党费记录？`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await removeDuesRecord(selected.duesId);
        message.success('删除成功');
        setSelected(null);
        fetchList();
        fetchStat();
      },
    });
  };

  const submitGenerate = async () => {
    setGenerating(true);
    try {
      await generateDuesRecords(genYear, genMonth);
      message.success(`${genYear}年${genMonth}月党费账单已生成`);
      setGenOpen(false);
      fetchList();
      fetchStat();
    } catch {
      // 错误提示由拦截器统一处理
    } finally {
      setGenerating(false);
    }
  };

  // ---------- 使用记录操作 ----------

  const openUseAdd = () => {
    useForm.resetFields();
    useForm.setFieldsValue({
      useYear: dayjs().year(),
      useMonth: dayjs().month() + 1,
    });
    setUseModalOpen(true);
  };

  const submitUse = async () => {
    const values = await useForm.validateFields();
    setUseSubmitting(true);
    try {
      await addDuesUse({
        ...values,
        useDate: values.useDate ? values.useDate.format('YYYY-MM-DD') : undefined,
      });
      message.success('新增成功');
      setUseModalOpen(false);
      useForm.resetFields();
      fetchUses();
    } catch {
      // 错误提示由拦截器统一处理
    } finally {
      setUseSubmitting(false);
    }
  };

  const confirmRemoveUse = () => {
    if (!useSelected) return;
    modal.confirm({
      title: `确认删除该笔党费使用记录（${useSelected.amount ?? 0} 元）？`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await removeDuesUse(useSelected.useId);
        message.success('删除成功');
        setUseSelected(null);
        fetchUses();
      },
    });
  };

  return (
    <div>
      {/* 收缴情况统计 */}
      <Row gutter={12} style={{ marginBottom: 12 }}>
        <Col xs={24} sm={12} lg={6}>
          <Card variant="borderless">
            <Statistic
              title={`${statYear} 年应收党费（元）`}
              value={stat?.shouldTotal ?? '—'}
              precision={2}
              valueStyle={{ color: '#C7000B' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card variant="borderless">
            <Statistic
              title={`${statYear} 年实收党费（元）`}
              value={stat?.paidTotal ?? '—'}
              precision={2}
              valueStyle={{ color: '#52C41A' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card variant="borderless">
            <Statistic title="未缴人数" value={stat?.unpaidCount ?? '—'} valueStyle={{ color: '#FF4D4F' }} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card variant="borderless">
            <Statistic title="已缴人数" value={stat?.paidCount ?? '—'} />
          </Card>
        </Col>
      </Row>

      <Card variant="borderless" styles={{ body: { padding: 16 } }}>
        <div style={{ marginBottom: 12 }}>
          <Space>
            <span style={{ color: '#8C8C8C' }}>统计年度</span>
            <Select
              style={{ width: 120 }}
              value={statYear}
              onChange={setStatYear}
              options={YEAR_OPTIONS}
            />
          </Space>
        </div>

        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[
            {
              key: 'record',
              label: '收缴记录',
              children: (
                <>
                  <Space wrap style={{ marginBottom: 12 }}>
                    <Input
                      placeholder="按姓名搜索"
                      allowClear
                      style={{ width: 160 }}
                      value={personName}
                      onChange={(e) => setPersonName(e.target.value)}
                      onPressEnter={doSearch}
                    />
                    <Select
                      placeholder="党组织"
                      allowClear
                      showSearch
                      optionFilterProp="label"
                      style={{ width: 180 }}
                      value={orgId}
                      onChange={setOrgId}
                      options={orgs.map((o) => ({ label: o.orgName, value: o.orgId }))}
                    />
                    <Select
                      placeholder="年度"
                      allowClear
                      style={{ width: 110 }}
                      value={duesYear}
                      onChange={setDuesYear}
                      options={YEAR_OPTIONS}
                    />
                    <Select
                      placeholder="月份"
                      allowClear
                      style={{ width: 100 }}
                      value={duesMonth}
                      onChange={setDuesMonth}
                      options={MONTH_OPTIONS}
                    />
                    <Select
                      placeholder="缴纳状态"
                      allowClear
                      style={{ width: 120 }}
                      value={status}
                      onChange={setStatus}
                      options={DUES_STATUS_OPTIONS}
                    />
                    <Button type="primary" icon={<SearchOutlined />} onClick={doSearch}>
                      查询
                    </Button>
                    <Button onClick={doReset}>重置</Button>
                    <Button icon={<ReloadOutlined />} onClick={fetchList}>
                      刷新
                    </Button>
                    {can('report:export') && (
                      <Button
                        icon={<DownloadOutlined />}
                        onClick={async () => {
                          try {
                            await exportDues({
                              orgId,
                              duesYear,
                              duesMonth,
                              status,
                              personName: personName || undefined,
                            });
                            message.success('导出已开始');
                          } catch {
                            // 错误提示由拦截器统一处理
                          }
                        }}
                      >
                        导出党费台账
                      </Button>
                    )}
                  </Space>

                  <Space wrap style={{ marginBottom: 12 }}>
                    {can('dues:generate') && (
                      <Button
                        type="primary"
                        ghost
                        icon={<FileAddOutlined />}
                        onClick={() => setGenOpen(true)}
                      >
                        生成月度账单
                      </Button>
                    )}
                    {can('dues:add') && (
                      <Button type="primary" icon={<PlusOutlined />} onClick={openAdd}>
                        新增记录
                      </Button>
                    )}
                    {can('dues:edit') && (
                      <Button
                        icon={<EditOutlined />}
                        disabled={!selected}
                        onClick={() => selected && openEdit(selected)}
                      >
                        编辑
                      </Button>
                    )}
                    {can('dues:remove') && (
                      <Button
                        icon={<DeleteOutlined />}
                        disabled={!selected}
                        onClick={confirmRemove}
                      >
                        删除
                      </Button>
                    )}
                  </Space>

                  <Spin spinning={loading}>
                    {rows.length === 0 && !loading ? (
                      <Empty description="暂无党费收缴记录" style={{ padding: '40px 0' }} />
                    ) : (
                      <Table
                        rowKey="duesId"
                        dataSource={rows}
                        size="middle"
                        pagination={{
                          current: pageNum,
                          pageSize: PAGE_SIZE,
                          total,
                          showSizeChanger: false,
                          showTotal: (t) => `共 ${t} 条`,
                          onChange: (p) => {
                            setPageNum(p);
                            setSelected(null);
                          },
                        }}
                        onRow={(record) => ({
                          onClick: () => setSelected(record),
                          style: {
                            cursor: 'pointer',
                            background:
                              selected?.duesId === record.duesId ? '#FFF5F5' : undefined,
                          },
                        })}
                        columns={[
                          { title: '姓名', dataIndex: 'personName', width: 110 },
                          { title: '党组织', dataIndex: 'orgName', width: 160, ellipsis: true },
                          {
                            title: '所属月份',
                            width: 110,
                            render: (_, r) => `${r.duesYear ?? '—'}-${r.duesMonth ?? '—'}`,
                          },
                          {
                            title: '计算基数',
                            dataIndex: 'duesBase',
                            width: 110,
                            render: (v?: number) => (v === undefined ? '—' : `${v}`),
                          },
                          {
                            title: '缴纳标准',
                            dataIndex: 'duesStandard',
                            width: 110,
                            render: (v?: number) => (v === undefined ? '—' : `${v}`),
                          },
                          {
                            title: '实缴金额',
                            dataIndex: 'duesPaid',
                            width: 110,
                            render: (v?: number) => (
                              <span style={{ color: '#C7000B', fontWeight: 600 }}>
                                {v === undefined ? '—' : v.toFixed(2)}
                              </span>
                            ),
                          },
                          {
                            title: '缴纳方式',
                            dataIndex: 'payType',
                            width: 110,
                            render: (v: number, r) =>
                              r.payTypeLabel ??
                              DUES_PAY_TYPE_OPTIONS.find((o) => o.value === v)?.label ??
                              '—',
                          },
                          { title: '缴纳日期', dataIndex: 'payDate', width: 120 },
                          {
                            title: '状态',
                            dataIndex: 'status',
                            width: 100,
                            render: (v: number, r) => (
                              <Space size={4}>
                                <Tag color={DUES_STATUS_COLORS[v] ?? 'default'}>
                                  {r.statusLabel ??
                                    DUES_STATUS_OPTIONS.find((o) => o.value === v)?.label ??
                                    '—'}
                                </Tag>
                                {r.isOverdue ? <Tag color="red">逾期</Tag> : null}
                              </Space>
                            ),
                          },
                        ]}
                      />
                    )}
                  </Spin>
                </>
              ),
            },
            {
              key: 'use',
              label: '使用记录',
              children: (
                <>
                  <Space wrap style={{ marginBottom: 12 }}>
                    <Select
                      placeholder="党组织"
                      allowClear
                      showSearch
                      optionFilterProp="label"
                      style={{ width: 180 }}
                      value={useOrgId}
                      onChange={setUseOrgId}
                      options={orgs.map((o) => ({ label: o.orgName, value: o.orgId }))}
                    />
                    <Select
                      placeholder="年度"
                      allowClear
                      style={{ width: 110 }}
                      value={useYear}
                      onChange={setUseYear}
                      options={YEAR_OPTIONS}
                    />
                    <Button icon={<ReloadOutlined />} onClick={fetchUses}>
                      刷新
                    </Button>
                    {can('dues:use:add') && (
                      <Button type="primary" icon={<PlusOutlined />} onClick={openUseAdd}>
                        新增使用记录
                      </Button>
                    )}
                    {can('dues:use:remove') && (
                      <Button
                        icon={<DeleteOutlined />}
                        disabled={!useSelected}
                        onClick={confirmRemoveUse}
                      >
                        删除
                      </Button>
                    )}
                  </Space>

                  <Spin spinning={useLoading}>
                    {useRows.length === 0 && !useLoading ? (
                      <Empty description="暂无党费使用记录" style={{ padding: '40px 0' }} />
                    ) : (
                      <Table
                        rowKey="useId"
                        dataSource={useRows}
                        size="middle"
                        pagination={{ pageSize: PAGE_SIZE, showSizeChanger: false }}
                        onRow={(record) => ({
                          onClick: () => setUseSelected(record),
                          style: {
                            cursor: 'pointer',
                            background:
                              useSelected?.useId === record.useId ? '#FFF5F5' : undefined,
                          },
                        })}
                        columns={[
                          { title: '党组织', dataIndex: 'orgName', width: 170, ellipsis: true },
                          {
                            title: '所属月份',
                            width: 110,
                            render: (_, r) => `${r.useYear ?? '—'}-${r.useMonth ?? '—'}`,
                          },
                          {
                            title: '金额（元）',
                            dataIndex: 'amount',
                            width: 120,
                            render: (v?: number) => (
                              <span style={{ color: '#C7000B', fontWeight: 600 }}>
                                {v === undefined ? '—' : v.toFixed(2)}
                              </span>
                            ),
                          },
                          {
                            title: '用途',
                            dataIndex: 'useCategory',
                            width: 130,
                            render: (v: number, r) => (
                              <Tag color="red">
                                {r.useCategoryLabel ??
                                  DUES_USE_CATEGORY_OPTIONS.find((o) => o.value === v)?.label ??
                                  '—'}
                              </Tag>
                            ),
                          },
                          { title: '用途说明', dataIndex: 'purpose', ellipsis: true },
                          { title: '使用日期', dataIndex: 'useDate', width: 120 },
                          { title: '审批人', dataIndex: 'approver', width: 100 },
                        ]}
                      />
                    )}
                  </Spin>
                </>
              ),
            },
          ]}
        />
      </Card>

      {/* 生成月度账单 */}
      <Modal
        open={genOpen}
        title="生成月度党费账单"
        onCancel={() => setGenOpen(false)}
        onOk={submitGenerate}
        confirmLoading={generating}
        okText="开始生成"
        cancelText="取消"
      >
        <div style={{ marginTop: 16 }}>
          <Space size={12}>
            <span>账单年度</span>
            <Select
              style={{ width: 130 }}
              value={genYear}
              onChange={setGenYear}
              options={YEAR_OPTIONS}
            />
            <span>账单月份</span>
            <Select
              style={{ width: 110 }}
              value={genMonth}
              onChange={setGenMonth}
              options={MONTH_OPTIONS}
            />
          </Space>
          <div style={{ marginTop: 12, color: '#8C8C8C', fontSize: 13 }}>
            将按党员党费计算基数为该月批量生成应收账单，已存在的记录不会重复生成。
          </div>
        </div>
      </Modal>

      {/* 新增 / 编辑收缴记录 */}
      <Modal
        open={modalOpen}
        title={editing ? '编辑党费收缴记录' : '新增党费收缴记录'}
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
            <Col span={8}>
              <Form.Item
                name="personId"
                label="党员"
                rules={[{ required: true, message: '请选择党员' }]}
              >
                <Select
                  placeholder="请选择党员"
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
            <Col span={8}>
              <Form.Item name="orgId" label="党组织">
                <Select
                  placeholder="请选择党组织"
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  options={orgs.map((o) => ({ label: o.orgName, value: o.orgId }))}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="duesYear" label="所属年度">
                <Select placeholder="请选择" allowClear options={YEAR_OPTIONS} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="duesMonth" label="所属月份">
                <Select placeholder="请选择" allowClear options={MONTH_OPTIONS} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="duesBase" label="计算基数（元）">
                <InputNumber min={0} precision={2} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="duesStandard" label="缴纳标准">
                <InputNumber min={0} precision={2} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="duesPaid" label="实缴金额（元）">
                <InputNumber min={0} precision={2} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="payType" label="缴纳方式">
                <Select placeholder="请选择" allowClear options={DUES_PAY_TYPE_OPTIONS} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="payDate" label="缴纳日期">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="status" label="缴纳状态">
                <Select placeholder="请选择" allowClear options={DUES_STATUS_OPTIONS} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>

      {/* 新增使用记录 */}
      <Modal
        open={useModalOpen}
        title="新增党费使用记录"
        onCancel={() => setUseModalOpen(false)}
        onOk={submitUse}
        confirmLoading={useSubmitting}
        okText="确定"
        cancelText="取消"
        width={680}
        destroyOnHidden
      >
        <Form form={useForm} layout="vertical" style={{ marginTop: 16 }}>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="orgId" label="使用党组织">
                <Select
                  placeholder="请选择党组织"
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  options={orgs.map((o) => ({ label: o.orgName, value: o.orgId }))}
                />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="useYear" label="年度">
                <Select placeholder="请选择" allowClear options={YEAR_OPTIONS} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="useMonth" label="月份">
                <Select placeholder="请选择" allowClear options={MONTH_OPTIONS} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="amount"
                label="使用金额（元）"
                rules={[{ required: true, message: '请输入使用金额' }]}
              >
                <InputNumber min={0} precision={2} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="useCategory" label="用途">
                <Select placeholder="请选择" allowClear options={DUES_USE_CATEGORY_OPTIONS} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="useDate" label="使用日期">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="approver" label="审批人">
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="purpose" label="用途说明">
            <Input.TextArea rows={3} placeholder="填写具体使用事项" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
