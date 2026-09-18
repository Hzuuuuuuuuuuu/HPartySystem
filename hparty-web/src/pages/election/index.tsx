import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Col,
  DatePicker,
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
  App as AntdApp,
} from 'antd';
import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { listDepts, listPersonOptions, type PersonOption, type SysDept } from '@/api/system';
import { useUserStore } from '@/store/user';
import {
  addElection,
  getElection,
  pageElections,
  removeElection,
  saveElectionCandidates,
  updateElection,
  ELECTION_POSITION_OPTIONS,
  ELECTION_STATUS_COLORS,
  ELECTION_STATUS_OPTIONS,
  ELECTION_TYPE_OPTIONS,
  type Election,
  type ElectionCandidate,
} from '@/api/election';

const PAGE_SIZE = 10;

export default function ElectionPage() {
  const { message, modal } = AntdApp.useApp();
  const can = useUserStore((s) => s.can);

  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<Election[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNum, setPageNum] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [orgId, setOrgId] = useState<number | undefined>();
  const [status, setStatus] = useState<number | undefined>();
  const [selected, setSelected] = useState<Election | null>(null);

  const [orgs, setOrgs] = useState<SysDept[]>([]);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Election | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  // 候选人抽屉
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [current, setCurrent] = useState<Election | null>(null);
  const [candidates, setCandidates] = useState<ElectionCandidate[]>([]);
  const [candLoading, setCandLoading] = useState(false);
  const [candSaving, setCandSaving] = useState(false);
  const [candModalOpen, setCandModalOpen] = useState(false);
  const [candIndex, setCandIndex] = useState<number | null>(null);
  const [persons, setPersons] = useState<PersonOption[]>([]);
  const [candForm] = Form.useForm();

  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      const res = await pageElections({
        pageNum,
        pageSize: PAGE_SIZE,
        keyword: keyword || undefined,
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
  }, [pageNum, keyword, orgId, status]);

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
    setSelected(null);
    fetchList();
  };

  const doReset = () => {
    setKeyword('');
    setOrgId(undefined);
    setStatus(undefined);
    setPageNum(1);
    setSelected(null);
  };

  const openAdd = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (row: Election) => {
    setEditing(row);
    form.setFieldsValue({
      ...row,
      planDate: row.planDate ? dayjs(row.planDate) : undefined,
      electionDate: row.electionDate ? dayjs(row.electionDate) : undefined,
    });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      const payload = {
        ...values,
        electionId: editing?.electionId,
        planDate: values.planDate ? values.planDate.format('YYYY-MM-DD') : undefined,
        electionDate: values.electionDate ? values.electionDate.format('YYYY-MM-DD') : undefined,
      };
      if (editing) {
        await updateElection(payload);
        message.success('修改成功');
      } else {
        await addElection(payload);
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

  const confirmRemove = (row?: Election | null) => {
    const target = row ?? selected;
    if (!target) return;
    modal.confirm({
      title: `确认删除「${target.title}」？`,
      content: '删除后该换届活动及其候选人名单将一并移除。',
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await removeElection(target.electionId);
        message.success('删除成功');
        if (selected?.electionId === target.electionId) setSelected(null);
        fetchList();
      },
    });
  };

  /** 打开候选人抽屉 */
  const openCandidates = async (row: Election) => {
    setCurrent(row);
    setDrawerOpen(true);
    setCandLoading(true);
    try {
      const detail = await getElection(row.electionId);
      setCandidates(detail?.candidates ?? []);
    } catch {
      setCandidates([]);
    } finally {
      setCandLoading(false);
    }
  };

  const openCandAdd = () => {
    setCandIndex(null);
    candForm.resetFields();
    candForm.setFieldsValue({ isIncumbent: 0, isElected: 0 });
    setCandModalOpen(true);
  };

  const openCandEdit = (index: number) => {
    setCandIndex(index);
    candForm.setFieldsValue(candidates[index]);
    setCandModalOpen(true);
  };

  const submitCandidate = async () => {
    const values = await candForm.validateFields();
    const person = persons.find((p) => p.personId === values.personId);
    const item: ElectionCandidate = {
      ...values,
      personName: person?.name,
    };
    setCandidates((prev) => {
      const next = [...prev];
      if (candIndex === null) next.push(item);
      else next[candIndex] = { ...next[candIndex], ...item };
      return next;
    });
    setCandModalOpen(false);
  };

  const removeCandidate = (index: number) => {
    setCandidates((prev) => prev.filter((_, i) => i !== index));
  };

  const saveCandidates = async () => {
    if (!current) return;
    setCandSaving(true);
    try {
      await saveElectionCandidates(current.electionId, candidates);
      message.success('候选人名单已保存');
      setDrawerOpen(false);
    } catch {
      // 错误提示由拦截器统一处理
    } finally {
      setCandSaving(false);
    }
  };

  return (
    <div>
      <Card variant="borderless" style={{ marginBottom: 12 }} styles={{ body: { padding: 16 } }}>
        <Space wrap>
          <Input
            placeholder="搜索换届标题"
            allowClear
            style={{ width: 200 }}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={doSearch}
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
            options={ELECTION_STATUS_OPTIONS}
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
        title="换届活动列表"
        extra={
          <Space>
            {can('election:add') && (
              <Button type="primary" icon={<PlusOutlined />} onClick={openAdd}>
                新增换届
              </Button>
            )}
            {can('election:edit') && (
              <Button
                icon={<EditOutlined />}
                disabled={!selected}
                onClick={() => selected && openEdit(selected)}
              >
                编辑
              </Button>
            )}
            {can('election:list') && (
              <Button
                icon={<TeamOutlined />}
                disabled={!selected}
                onClick={() => selected && openCandidates(selected)}
              >
                候选人
              </Button>
            )}
            {can('election:remove') && (
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
            <Empty description="暂无换届数据" style={{ padding: '40px 0' }} />
          ) : (
            <Table
              rowKey="electionId"
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
                  background: selected?.electionId === record.electionId ? '#FFF5F5' : undefined,
                },
              })}
              columns={[
                { title: '换届标题', dataIndex: 'title', ellipsis: true },
                { title: '党组织', dataIndex: 'orgName', width: 160, ellipsis: true },
                {
                  title: '类型',
                  dataIndex: 'electionType',
                  width: 110,
                  render: (v: number, r) => (
                    <Tag color="red">
                      {r.electionTypeLabel ??
                        ELECTION_TYPE_OPTIONS.find((o) => o.value === v)?.label ??
                        '—'}
                    </Tag>
                  ),
                },
                {
                  title: '届次',
                  dataIndex: 'termNo',
                  width: 90,
                  render: (v?: number) => (v ? `第 ${v} 届` : '—'),
                },
                { title: '计划日期', dataIndex: 'planDate', width: 120 },
                { title: '选举日期', dataIndex: 'electionDate', width: 120 },
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
                    <Tag color={ELECTION_STATUS_COLORS[v] ?? 'default'}>
                      {r.statusLabel ??
                        ELECTION_STATUS_OPTIONS.find((o) => o.value === v)?.label ??
                        '—'}
                    </Tag>
                  ),
                },
                {
                  title: '操作',
                  width: 150,
                  fixed: 'right',
                  render: (_, r) => (
                    <Space size={0}>
                      <Button
                        type="link"
                        size="small"
                        onClick={(e) => {
                          e.stopPropagation();
                          openCandidates(r);
                        }}
                      >
                        候选人
                      </Button>
                      {can('election:remove') && (
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

      {/* 新增 / 编辑换届 */}
      <Modal
        open={modalOpen}
        title={editing ? '编辑换届活动' : '新增换届活动'}
        onCancel={() => {
          setModalOpen(false);
          setEditing(null);
        }}
        onOk={submit}
        confirmLoading={submitting}
        okText="确定"
        cancelText="取消"
        width={760}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Row gutter={12}>
            <Col span={16}>
              <Form.Item
                name="title"
                label="换届标题"
                rules={[{ required: true, message: '请输入换届标题' }]}
              >
                <Input placeholder="如：中共XX支部委员会换届选举" />
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
              <Form.Item name="electionType" label="换届类型">
                <Select placeholder="请选择" allowClear options={ELECTION_TYPE_OPTIONS} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="termNo" label="届次">
                <InputNumber min={1} precision={0} style={{ width: '100%' }} placeholder="如：5" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="status" label="状态">
                <Select placeholder="请选择" allowClear options={ELECTION_STATUS_OPTIONS} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="planDate" label="计划日期">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="electionDate" label="选举日期">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="place" label="会议地点">
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="hostName" label="主持人">
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="shouldAttend" label="应到人数">
                <InputNumber min={0} precision={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="actualAttend" label="实到人数">
                <InputNumber min={0} precision={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="reason" label="换届原因">
            <Input.TextArea rows={2} placeholder="如：本届支委会任期届满" />
          </Form.Item>
          <Form.Item name="resultSummary" label="结果摘要">
            <Input.TextArea rows={3} placeholder="选举结果概述" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 候选人名单 */}
      <Drawer
        open={drawerOpen}
        title={`候选人名单${current ? ` · ${current.title}` : ''}`}
        width={860}
        onClose={() => setDrawerOpen(false)}
        extra={
          <Space>
            <Button icon={<PlusOutlined />} onClick={openCandAdd}>
              添加候选人
            </Button>
            <Button type="primary" loading={candSaving} onClick={saveCandidates}>
              保存名单
            </Button>
          </Space>
        }
      >
        <Spin spinning={candLoading}>
          {candidates.length === 0 && !candLoading ? (
            <Empty description="暂无候选人" style={{ padding: '40px 0' }} />
          ) : (
            <Table
              rowKey={(r) => String(r.candidateId ?? r.personId)}
              dataSource={candidates}
              size="middle"
              pagination={false}
              columns={[
                { title: '姓名', dataIndex: 'personName', width: 120 },
                {
                  title: '拟任职务',
                  dataIndex: 'positionCode',
                  width: 130,
                  render: (v: string, r: ElectionCandidate) =>
                    r.positionName ??
                    ELECTION_POSITION_OPTIONS.find((o) => o.value === v)?.label ??
                    '—',
                },
                { title: '得票数', dataIndex: 'votes', width: 90 },
                {
                  title: '现任',
                  dataIndex: 'isIncumbent',
                  width: 80,
                  render: (v?: number) => (v ? <Tag color="blue">现任</Tag> : '—'),
                },
                {
                  title: '是否当选',
                  dataIndex: 'isElected',
                  width: 100,
                  render: (v?: number) =>
                    v ? <Tag color="success">当选</Tag> : <Tag>未当选</Tag>,
                },
                {
                  title: '操作',
                  width: 130,
                  render: (_, __, index) => (
                    <Space size={0}>
                      <Button type="link" size="small" onClick={() => openCandEdit(index)}>
                        编辑
                      </Button>
                      <Button
                        type="link"
                        size="small"
                        danger
                        onClick={() => removeCandidate(index)}
                      >
                        移除
                      </Button>
                    </Space>
                  ),
                },
              ]}
            />
          )}
        </Spin>
      </Drawer>

      {/* 候选人表单 */}
      <Modal
        open={candModalOpen}
        title={candIndex === null ? '添加候选人' : '编辑候选人'}
        onCancel={() => setCandModalOpen(false)}
        onOk={submitCandidate}
        okText="确定"
        cancelText="取消"
        destroyOnHidden
      >
        <Form form={candForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="personId"
            label="人员"
            rules={[{ required: true, message: '请选择人员' }]}
          >
            <Select
              placeholder="请选择人员"
              showSearch
              optionFilterProp="label"
              disabled={candIndex !== null}
              options={persons.map((p) => ({
                label: `${p.name}${p.orgName ? `（${p.orgName}）` : ''}`,
                value: p.personId,
              }))}
            />
          </Form.Item>
          <Form.Item name="positionCode" label="拟任职务">
            <Select placeholder="请选择职务" allowClear options={ELECTION_POSITION_OPTIONS} />
          </Form.Item>
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="votes" label="得票数">
                <InputNumber min={0} precision={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="isIncumbent" label="是否现任">
                <Select
                  allowClear
                  placeholder="请选择"
                  options={[
                    { label: '是', value: 1 },
                    { label: '否', value: 0 },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="isElected" label="是否当选">
                <Select
                  allowClear
                  placeholder="请选择"
                  options={[
                    { label: '当选', value: 1 },
                    { label: '未当选', value: 0 },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </div>
  );
}
