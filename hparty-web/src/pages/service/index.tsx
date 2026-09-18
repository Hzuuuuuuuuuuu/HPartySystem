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
  Tag,
  App as AntdApp,
} from 'antd';
import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  WalletOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { listDepts, listPersonOptions, type PersonOption, type SysDept } from '@/api/system';
import { useUserStore } from '@/store/user';
import {
  addService,
  getServiceStatistics,
  pageServices,
  removeService,
  updateService,
  SERVICE_STATUS_COLORS,
  SERVICE_STATUS_OPTIONS,
  SERVICE_TYPE_OPTIONS,
  type PartyService,
  type ServiceStatistics,
} from '@/api/service';

const PAGE_SIZE = 10;

export default function ServicePage() {
  const { message, modal } = AntdApp.useApp();
  const can = useUserStore((s) => s.can);

  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<PartyService[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNum, setPageNum] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [orgId, setOrgId] = useState<number | undefined>();
  const [serviceType, setServiceType] = useState<number | undefined>();
  const [status, setStatus] = useState<number | undefined>();
  const [personName, setPersonName] = useState('');
  const [selected, setSelected] = useState<PartyService | null>(null);

  const [stat, setStat] = useState<ServiceStatistics | null>(null);
  const [orgs, setOrgs] = useState<SysDept[]>([]);
  const [persons, setPersons] = useState<PersonOption[]>([]);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<PartyService | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      const res = await pageServices({
        pageNum,
        pageSize: PAGE_SIZE,
        keyword: keyword || undefined,
        orgId,
        serviceType,
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
  }, [pageNum, keyword, orgId, serviceType, status, personName]);

  useEffect(() => {
    fetchList();
  }, [fetchList]);

  useEffect(() => {
    getServiceStatistics()
      .then(setStat)
      .catch(() => setStat(null));
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
    setKeyword('');
    setPersonName('');
    setOrgId(undefined);
    setServiceType(undefined);
    setStatus(undefined);
    setPageNum(1);
    setSelected(null);
  };

  const openAdd = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ status: 0 });
    setModalOpen(true);
  };

  const openEdit = (row: PartyService) => {
    setEditing(row);
    form.setFieldsValue({
      ...row,
      serviceDate: row.serviceDate ? dayjs(row.serviceDate) : undefined,
    });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      const person = persons.find((p) => p.personId === values.personId);
      const payload = {
        ...values,
        serviceId: editing?.serviceId,
        personName: person?.name ?? values.personName,
        serviceDate: values.serviceDate ? values.serviceDate.format('YYYY-MM-DD') : undefined,
      };
      if (editing) {
        await updateService(payload);
        message.success('修改成功');
      } else {
        await addService(payload);
        message.success('新增成功');
      }
      setModalOpen(false);
      setEditing(null);
      form.resetFields();
      fetchList();
      getServiceStatistics()
        .then(setStat)
        .catch(() => undefined);
    } catch {
      // 错误提示由拦截器统一处理
    } finally {
      setSubmitting(false);
    }
  };

  const confirmRemove = (row?: PartyService | null) => {
    const target = row ?? selected;
    if (!target) return;
    modal.confirm({
      title: `确认删除「${target.title}」？`,
      content: '删除后该服务记录将不再展示。',
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await removeService(target.serviceId);
        message.success('删除成功');
        if (selected?.serviceId === target.serviceId) setSelected(null);
        fetchList();
      },
    });
  };

  return (
    <div>
      {/* 服务统计 */}
      <Row gutter={12} style={{ marginBottom: 12 }}>
        <Col xs={24} sm={12} lg={6}>
          <Card variant="borderless">
            <Statistic title="服务记录总数" value={stat?.total ?? '—'} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card variant="borderless">
            <Statistic
              title="累计帮扶金额（元）"
              value={stat?.totalAmount ?? '—'}
              precision={2}
              valueStyle={{ color: '#C7000B' }}
              prefix={<WalletOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card variant="borderless" title="按类型分布" styles={{ body: { paddingTop: 12 } }}>
            {stat?.byType?.length ? (
              <Space wrap size={[4, 8]}>
                {stat.byType.map((t) => (
                  <Tag key={String(t.type)} color="red">
                    {t.typeLabel ??
                      SERVICE_TYPE_OPTIONS.find((o) => o.value === t.type)?.label ??
                      t.type}
                    ：{t.count}
                  </Tag>
                ))}
              </Space>
            ) : (
              <span style={{ color: '#8C8C8C' }}>暂无数据</span>
            )}
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card variant="borderless" title="按状态分布" styles={{ body: { paddingTop: 12 } }}>
            {stat?.byStatus?.length ? (
              <Space wrap size={[4, 8]}>
                {stat.byStatus.map((s) => (
                  <Tag key={String(s.status)} color={SERVICE_STATUS_COLORS[Number(s.status)] ?? 'blue'}>
                    {s.statusLabel ??
                      SERVICE_STATUS_OPTIONS.find((o) => o.value === Number(s.status))?.label ??
                      s.status}
                    ：{s.count}
                  </Tag>
                ))}
              </Space>
            ) : (
              <span style={{ color: '#8C8C8C' }}>暂无数据</span>
            )}
          </Card>
        </Col>
      </Row>

      <Card variant="borderless" style={{ marginBottom: 12 }} styles={{ body: { padding: 16 } }}>
        <Space wrap>
          <Input
            placeholder="搜索服务标题"
            allowClear
            style={{ width: 190 }}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={doSearch}
          />
          <Input
            placeholder="按服务对象姓名搜索"
            allowClear
            style={{ width: 180 }}
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
            placeholder="服务类型"
            allowClear
            style={{ width: 140 }}
            value={serviceType}
            onChange={setServiceType}
            options={SERVICE_TYPE_OPTIONS}
          />
          <Select
            placeholder="状态"
            allowClear
            style={{ width: 120 }}
            value={status}
            onChange={setStatus}
            options={SERVICE_STATUS_OPTIONS}
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
        title="党员服务记录"
        extra={
          <Space>
            {can('service:add') && (
              <Button type="primary" icon={<PlusOutlined />} onClick={openAdd}>
                新增服务
              </Button>
            )}
            {can('service:edit') && (
              <Button
                icon={<EditOutlined />}
                disabled={!selected}
                onClick={() => selected && openEdit(selected)}
              >
                编辑
              </Button>
            )}
            {can('service:remove') && (
              <Button
                icon={<DeleteOutlined />}
                disabled={!selected}
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
            <Empty description="暂无党员服务记录" style={{ padding: '40px 0' }} />
          ) : (
            <Table
              rowKey="serviceId"
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
                  background: selected?.serviceId === record.serviceId ? '#FFF5F5' : undefined,
                },
              })}
              columns={[
                { title: '服务事项', dataIndex: 'title', ellipsis: true },
                {
                  title: '服务类型',
                  dataIndex: 'serviceType',
                  width: 120,
                  render: (v: number, r) => (
                    <Tag color="red">
                      {r.serviceTypeLabel ??
                        SERVICE_TYPE_OPTIONS.find((o) => o.value === v)?.label ??
                        '—'}
                    </Tag>
                  ),
                },
                { title: '服务对象', dataIndex: 'personName', width: 110 },
                { title: '党组织', dataIndex: 'orgName', width: 150, ellipsis: true },
                { title: '服务日期', dataIndex: 'serviceDate', width: 120 },
                {
                  title: '金额（元）',
                  dataIndex: 'amount',
                  width: 110,
                  render: (v?: number) => (v === undefined ? '—' : v.toFixed(2)),
                },
                { title: '经办人', dataIndex: 'handlerName', width: 100 },
                {
                  title: '状态',
                  dataIndex: 'status',
                  width: 100,
                  render: (v: number, r) => (
                    <Tag color={SERVICE_STATUS_COLORS[v] ?? 'default'}>
                      {r.statusLabel ??
                        SERVICE_STATUS_OPTIONS.find((o) => o.value === v)?.label ??
                        '—'}
                    </Tag>
                  ),
                },
                {
                  title: '操作',
                  width: 130,
                  fixed: 'right',
                  render: (_, r) => (
                    <Space size={0}>
                      {can('service:edit') && (
                        <Button
                          type="link"
                          size="small"
                          onClick={(e) => {
                            e.stopPropagation();
                            openEdit(r);
                          }}
                        >
                          编辑
                        </Button>
                      )}
                      {can('service:remove') && (
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

      {/* 新增 / 编辑服务记录 */}
      <Modal
        open={modalOpen}
        title={editing ? '编辑党员服务' : '新增党员服务'}
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
            label="服务事项"
            rules={[{ required: true, message: '请输入服务事项' }]}
          >
            <Input placeholder="如：走访慰问困难党员" />
          </Form.Item>

          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="serviceType" label="服务类型">
                <Select placeholder="请选择" allowClear options={SERVICE_TYPE_OPTIONS} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="personId" label="服务对象">
                <Select
                  placeholder="请选择人员"
                  allowClear
                  showSearch
                  optionFilterProp="label"
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
              <Form.Item name="serviceDate" label="服务日期">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="amount" label="涉及金额（元）">
                <InputNumber min={0} precision={2} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="handlerName" label="经办人">
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="status" label="处理状态">
                <Select placeholder="请选择" allowClear options={SERVICE_STATUS_OPTIONS} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="content" label="服务内容">
            <Input.TextArea rows={3} placeholder="填写服务开展的具体内容" />
          </Form.Item>
          <Form.Item name="result" label="处理结果">
            <Input.TextArea rows={2} placeholder="填写办理结果" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
