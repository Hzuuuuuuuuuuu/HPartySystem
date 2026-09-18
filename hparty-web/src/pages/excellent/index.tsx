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
  TrophyOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { listDepts, listPersonOptions, type PersonOption, type SysDept } from '@/api/system';
import { useUserStore } from '@/store/user';
import {
  addExcellentSelection,
  getExcellentSelection,
  listExcellentCandidates,
  pageExcellentSelections,
  removeExcellentCandidate,
  removeExcellentSelection,
  saveExcellentCandidate,
  updateExcellentSelection,
  EXCELLENT_RESULT_COLORS,
  EXCELLENT_RESULT_OPTIONS,
  EXCELLENT_STATUS_COLORS,
  EXCELLENT_STATUS_OPTIONS,
  EXCELLENT_TYPE_OPTIONS,
  type ExcellentCandidate,
  type ExcellentSelection,
} from '@/api/excellent';

const PAGE_SIZE = 10;

/** 年度下拉：今年向前推 10 年 */
const YEAR_OPTIONS = Array.from({ length: 10 }, (_, i) => {
  const y = dayjs().year() - i;
  return { label: `${y} 年`, value: y };
});

export default function ExcellentPage() {
  const { message, modal } = AntdApp.useApp();
  const can = useUserStore((s) => s.can);

  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<ExcellentSelection[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNum, setPageNum] = useState(1);
  const [orgId, setOrgId] = useState<number | undefined>();
  const [selectionType, setSelectionType] = useState<number | undefined>();
  const [selectionYear, setSelectionYear] = useState<number | undefined>();
  const [status, setStatus] = useState<number | undefined>();
  const [selected, setSelected] = useState<ExcellentSelection | null>(null);

  const [orgs, setOrgs] = useState<SysDept[]>([]);
  const [persons, setPersons] = useState<PersonOption[]>([]);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ExcellentSelection | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  // 候选人抽屉
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [current, setCurrent] = useState<ExcellentSelection | null>(null);
  const [candidates, setCandidates] = useState<ExcellentCandidate[]>([]);
  const [candLoading, setCandLoading] = useState(false);
  const [candModalOpen, setCandModalOpen] = useState(false);
  const [editingCand, setEditingCand] = useState<ExcellentCandidate | null>(null);
  const [candSubmitting, setCandSubmitting] = useState(false);
  const [candForm] = Form.useForm();

  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      const res = await pageExcellentSelections({
        pageNum,
        pageSize: PAGE_SIZE,
        orgId,
        selectionType,
        selectionYear,
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
  }, [pageNum, orgId, selectionType, selectionYear, status]);

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
    setOrgId(undefined);
    setSelectionType(undefined);
    setSelectionYear(undefined);
    setStatus(undefined);
    setPageNum(1);
    setSelected(null);
  };

  // ---------- 评选活动 ----------

  const openAdd = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ selectionYear: dayjs().year(), status: 0 });
    setModalOpen(true);
  };

  const openEdit = (row: ExcellentSelection) => {
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
        selectionId: editing?.selectionId,
        startDate: values.startDate ? values.startDate.format('YYYY-MM-DD') : undefined,
        endDate: values.endDate ? values.endDate.format('YYYY-MM-DD') : undefined,
      };
      if (editing) {
        await updateExcellentSelection(payload);
        message.success('修改成功');
      } else {
        await addExcellentSelection(payload);
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

  const confirmRemove = (row?: ExcellentSelection | null) => {
    const target = row ?? selected;
    if (!target) return;
    modal.confirm({
      title: `确认删除「${target.title}」？`,
      content: '删除后该评选活动及其候选人名单将一并移除。',
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await removeExcellentSelection(target.selectionId);
        message.success('删除成功');
        if (selected?.selectionId === target.selectionId) setSelected(null);
        fetchList();
      },
    });
  };

  // ---------- 候选人 ----------

  const fetchCandidates = useCallback(async (selectionId: number) => {
    setCandLoading(true);
    try {
      setCandidates((await listExcellentCandidates(selectionId)) ?? []);
    } catch {
      setCandidates([]);
    } finally {
      setCandLoading(false);
    }
  }, []);

  const openCandidates = async (row: ExcellentSelection) => {
    setCurrent(row);
    setDrawerOpen(true);
    setCandLoading(true);
    try {
      // 详情接口同时返回候选人，失败时回退到候选人列表接口
      const detail = await getExcellentSelection(row.selectionId);
      if (detail?.candidates?.length) setCandidates(detail.candidates);
      else await fetchCandidates(row.selectionId);
    } catch {
      await fetchCandidates(row.selectionId);
    } finally {
      setCandLoading(false);
    }
  };

  const openCandAdd = () => {
    setEditingCand(null);
    candForm.resetFields();
    candForm.setFieldsValue({ result: 0 });
    setCandModalOpen(true);
  };

  const openCandEdit = (row: ExcellentCandidate) => {
    setEditingCand(row);
    candForm.setFieldsValue({ ...row, personName: row.personName });
    setCandModalOpen(true);
  };

  const submitCandidate = async () => {
    if (!current) return;
    const values = await candForm.validateFields();
    setCandSubmitting(true);
    try {
      const person = persons.find((p) => p.personId === values.personId);
      await saveExcellentCandidate({
        ...values,
        candidateId: editingCand?.candidateId,
        selectionId: current.selectionId,
        personName: person?.name ?? editingCand?.personName,
      });
      message.success(editingCand ? '修改成功' : '新增成功');
      setCandModalOpen(false);
      setEditingCand(null);
      candForm.resetFields();
      fetchCandidates(current.selectionId);
    } catch {
      // 错误提示由拦截器统一处理
    } finally {
      setCandSubmitting(false);
    }
  };

  const confirmRemoveCandidate = (row: ExcellentCandidate) => {
    if (!row.candidateId) return;
    modal.confirm({
      title: `确认移除候选人「${row.personName ?? ''}」？`,
      okText: '确认移除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await removeExcellentCandidate(row.candidateId as number);
        message.success('已移除');
        if (current) fetchCandidates(current.selectionId);
      },
    });
  };

  /** 候选人是否使用党组织维度（先进基层党组织按组织推荐） */
  const isOrgCandidate = current?.selectionType === 3;

  return (
    <div>
      <Card variant="borderless" style={{ marginBottom: 12 }} styles={{ body: { padding: 16 } }}>
        <Space wrap>
          <Select
            placeholder="党组织"
            allowClear
            showSearch
            optionFilterProp="label"
            style={{ width: 190 }}
            value={orgId}
            onChange={setOrgId}
            options={orgs.map((o) => ({ label: o.orgName, value: o.orgId }))}
          />
          <Select
            placeholder="评选类型"
            allowClear
            style={{ width: 170 }}
            value={selectionType}
            onChange={setSelectionType}
            options={EXCELLENT_TYPE_OPTIONS}
          />
          <Select
            placeholder="评选年度"
            allowClear
            style={{ width: 130 }}
            value={selectionYear}
            onChange={setSelectionYear}
            options={YEAR_OPTIONS}
          />
          <Select
            placeholder="状态"
            allowClear
            style={{ width: 130 }}
            value={status}
            onChange={setStatus}
            options={EXCELLENT_STATUS_OPTIONS}
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
        title="评选活动列表"
        extra={
          <Space>
            {can('excellent:add') && (
              <Button type="primary" icon={<PlusOutlined />} onClick={openAdd}>
                新增评选
              </Button>
            )}
            {can('excellent:edit') && (
              <Button
                icon={<EditOutlined />}
                disabled={!selected}
                onClick={() => selected && openEdit(selected)}
              >
                编辑
              </Button>
            )}
            {can('excellent:list') && (
              <Button
                icon={<TrophyOutlined />}
                disabled={!selected}
                onClick={() => selected && openCandidates(selected)}
              >
                候选人名单
              </Button>
            )}
            {can('excellent:remove') && (
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
            <Empty description="暂无评选活动" style={{ padding: '40px 0' }} />
          ) : (
            <Table
              rowKey="selectionId"
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
                onDoubleClick: () => openCandidates(record),
                style: {
                  cursor: 'pointer',
                  background:
                    selected?.selectionId === record.selectionId ? '#FFF5F5' : undefined,
                },
              })}
              columns={[
                { title: '评选活动', dataIndex: 'title', ellipsis: true },
                {
                  title: '评选类型',
                  dataIndex: 'selectionType',
                  width: 150,
                  render: (v: number, r) => (
                    <Tag color="red">
                      {r.selectionTypeLabel ??
                        EXCELLENT_TYPE_OPTIONS.find((o) => o.value === v)?.label ??
                        '—'}
                    </Tag>
                  ),
                },
                { title: '党组织', dataIndex: 'orgName', width: 150, ellipsis: true },
                {
                  title: '年度',
                  dataIndex: 'selectionYear',
                  width: 90,
                  render: (v?: number) => (v ? `${v} 年` : '—'),
                },
                {
                  title: '起止日期',
                  width: 200,
                  render: (_, r) => `${r.startDate ?? '—'} ~ ${r.endDate ?? '—'}`,
                },
                {
                  title: '名额',
                  dataIndex: 'quota',
                  width: 80,
                  render: (v?: number) => v ?? '—',
                },
                {
                  title: '状态',
                  dataIndex: 'status',
                  width: 100,
                  render: (v: number, r) => (
                    <Tag color={EXCELLENT_STATUS_COLORS[v] ?? 'default'}>
                      {r.statusLabel ??
                        EXCELLENT_STATUS_OPTIONS.find((o) => o.value === v)?.label ??
                        '—'}
                    </Tag>
                  ),
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
                          openCandidates(r);
                        }}
                      >
                        候选人
                      </Button>
                      {can('excellent:remove') && (
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

      {/* 新增 / 编辑评选活动 */}
      <Modal
        open={modalOpen}
        title={editing ? '编辑评选活动' : '新增评选活动'}
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
            label="评选活动名称"
            rules={[{ required: true, message: '请输入评选活动名称' }]}
          >
            <Input placeholder="如：2026年度优秀共产党员评选" />
          </Form.Item>

          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="selectionType" label="评选类型">
                <Select placeholder="请选择" allowClear options={EXCELLENT_TYPE_OPTIONS} />
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
              <Form.Item name="selectionYear" label="评选年度">
                <Select placeholder="请选择" allowClear options={YEAR_OPTIONS} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="startDate" label="开始日期">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="endDate" label="结束日期">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="quota" label="表彰名额">
                <InputNumber min={0} precision={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="status" label="状态">
                <Select placeholder="请选择" allowClear options={EXCELLENT_STATUS_OPTIONS} />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="description" label="评选说明">
            <Input.TextArea rows={4} placeholder="填写评选条件、程序与要求" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 候选人名单 */}
      <Drawer
        open={drawerOpen}
        title={`候选人名单${current ? ` · ${current.title}` : ''}`}
        width={900}
        onClose={() => setDrawerOpen(false)}
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCandAdd}>
            添加候选人
          </Button>
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
                { title: '姓名', dataIndex: 'personName', width: 110 },
                { title: '所在党组织', dataIndex: 'orgName', width: 150, ellipsis: true },
                {
                  title: '推荐党组织',
                  dataIndex: 'recommendOrgName',
                  width: 150,
                  ellipsis: true,
                  render: (v?: string) => v ?? '—',
                },
                { title: '主要事迹', dataIndex: 'deeds', ellipsis: true },
                {
                  title: '得票',
                  dataIndex: 'votes',
                  width: 80,
                  render: (v?: number) => v ?? '—',
                },
                {
                  title: '排名',
                  dataIndex: 'rankNo',
                  width: 80,
                  render: (v?: number) => v ?? '—',
                },
                {
                  title: '评选结果',
                  dataIndex: 'result',
                  width: 110,
                  render: (v: number, r) => (
                    <Tag color={EXCELLENT_RESULT_COLORS[v] ?? 'default'}>
                      {r.resultLabel ??
                        EXCELLENT_RESULT_OPTIONS.find((o) => o.value === v)?.label ??
                        '—'}
                    </Tag>
                  ),
                },
                {
                  title: '操作',
                  width: 130,
                  render: (_, r) => (
                    <Space size={0}>
                      <Button type="link" size="small" onClick={() => openCandEdit(r)}>
                        编辑
                      </Button>
                      <Button
                        type="link"
                        size="small"
                        danger
                        onClick={() => confirmRemoveCandidate(r)}
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
        title={editingCand ? '编辑候选人' : '添加候选人'}
        onCancel={() => {
          setCandModalOpen(false);
          setEditingCand(null);
        }}
        onOk={submitCandidate}
        confirmLoading={candSubmitting}
        okText="确定"
        cancelText="取消"
        width={640}
        destroyOnHidden
      >
        <Form form={candForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="personId"
            label={isOrgCandidate ? '推荐对象' : '候选人'}
            rules={[{ required: true, message: '请选择人员' }]}
          >
            <Select
              placeholder="请选择人员"
              showSearch
              optionFilterProp="label"
              disabled={!!editingCand}
              options={persons.map((p) => ({
                label: `${p.name}${p.orgName ? `（${p.orgName}）` : ''}`,
                value: p.personId,
              }))}
            />
          </Form.Item>
          <Form.Item name="recommendOrgId" label="推荐党组织">
            <Select
              placeholder="请选择党组织"
              allowClear
              showSearch
              optionFilterProp="label"
              options={orgs.map((o) => ({ label: o.orgName, value: o.orgId }))}
            />
          </Form.Item>
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="votes" label="得票数">
                <InputNumber min={0} precision={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="rankNo" label="排名">
                <InputNumber min={1} precision={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="result" label="评选结果">
                <Select placeholder="请选择" allowClear options={EXCELLENT_RESULT_OPTIONS} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="deeds" label="主要事迹">
            <Input.TextArea rows={4} placeholder="填写推荐对象的主要事迹" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
