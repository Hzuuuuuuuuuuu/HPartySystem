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
  addDiscipline,
  listDisciplineParticipants,
  pageDisciplines,
  removeDiscipline,
  saveDisciplineParticipants,
  updateDiscipline,
  DISCIPLINE_ATTEND_OPTIONS,
  DISCIPLINE_TYPE_OPTIONS,
  type DisciplineParticipant,
  type DisciplineStudy,
} from '@/api/discipline';

const PAGE_SIZE = 10;

const ATTEND_STATUS_TEXT: Record<number, string> = {
  0: '未参加',
  1: '已参加',
  2: '请假',
  3: '缺席',
};

export default function DisciplinePage() {
  const { message, modal } = AntdApp.useApp();
  const can = useUserStore((s) => s.can);

  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<DisciplineStudy[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNum, setPageNum] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [orgId, setOrgId] = useState<number | undefined>();
  const [studyType, setStudyType] = useState<number | undefined>();
  const [selected, setSelected] = useState<DisciplineStudy | null>(null);

  const [orgs, setOrgs] = useState<SysDept[]>([]);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<DisciplineStudy | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  // 参学人员抽屉
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [current, setCurrent] = useState<DisciplineStudy | null>(null);
  const [participants, setParticipants] = useState<DisciplineParticipant[]>([]);
  const [partLoading, setPartLoading] = useState(false);
  const [partSaving, setPartSaving] = useState(false);
  const [partModalOpen, setPartModalOpen] = useState(false);
  const [partIndex, setPartIndex] = useState<number | null>(null);
  const [persons, setPersons] = useState<PersonOption[]>([]);
  const [partForm] = Form.useForm();

  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      const res = await pageDisciplines({
        pageNum,
        pageSize: PAGE_SIZE,
        keyword: keyword || undefined,
        orgId,
        studyType,
      });
      setRows(res.records ?? []);
      setTotal(res.total ?? 0);
    } catch {
      setRows([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [pageNum, keyword, orgId, studyType]);

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
    setStudyType(undefined);
    setPageNum(1);
    setSelected(null);
  };

  const openAdd = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (row: DisciplineStudy) => {
    setEditing(row);
    form.setFieldsValue({
      ...row,
      studyDate: row.studyDate ? dayjs(row.studyDate) : undefined,
    });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      const payload = {
        ...values,
        studyId: editing?.studyId,
        studyDate: values.studyDate ? values.studyDate.format('YYYY-MM-DD') : undefined,
      };
      if (editing) {
        await updateDiscipline(payload);
        message.success('修改成功');
      } else {
        await addDiscipline(payload);
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

  const confirmRemove = (row?: DisciplineStudy | null) => {
    const target = row ?? selected;
    if (!target) return;
    modal.confirm({
      title: `确认删除「${target.title}」？`,
      content: '删除后该党纪学习记录及其参学名单将一并移除。',
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await removeDiscipline(target.studyId);
        message.success('删除成功');
        if (selected?.studyId === target.studyId) setSelected(null);
        fetchList();
      },
    });
  };

  /** 打开参学人员抽屉 */
  const openParticipants = async (row: DisciplineStudy) => {
    setCurrent(row);
    setDrawerOpen(true);
    setPartLoading(true);
    try {
      setParticipants((await listDisciplineParticipants(row.studyId)) ?? []);
    } catch {
      setParticipants([]);
    } finally {
      setPartLoading(false);
    }
  };

  const openPartAdd = () => {
    setPartIndex(null);
    partForm.resetFields();
    partForm.setFieldsValue({ attendStatus: 1, isPassed: 1 });
    setPartModalOpen(true);
  };

  const openPartEdit = (index: number) => {
    setPartIndex(index);
    partForm.setFieldsValue(participants[index]);
    setPartModalOpen(true);
  };

  const submitParticipant = async () => {
    const values = await partForm.validateFields();
    const person = persons.find((p) => p.personId === values.personId);
    const item: DisciplineParticipant = { ...values, personName: person?.name };
    setParticipants((prev) => {
      const next = [...prev];
      if (partIndex === null) next.push(item);
      else next[partIndex] = { ...next[partIndex], ...item };
      return next;
    });
    setPartModalOpen(false);
  };

  const saveParticipants = async () => {
    if (!current) return;
    setPartSaving(true);
    try {
      await saveDisciplineParticipants(current.studyId, participants);
      message.success('参学名单已保存');
      setDrawerOpen(false);
      fetchList();
    } catch {
      // 错误提示由拦截器统一处理
    } finally {
      setPartSaving(false);
    }
  };

  return (
    <div>
      <Card variant="borderless" style={{ marginBottom: 12 }} styles={{ body: { padding: 16 } }}>
        <Space wrap>
          <Input
            placeholder="搜索学习主题"
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
            placeholder="学习类型"
            allowClear
            style={{ width: 150 }}
            value={studyType}
            onChange={setStudyType}
            options={DISCIPLINE_TYPE_OPTIONS}
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
        title="党纪学习教育记录"
        extra={
          <Space>
            {can('discipline:add') && (
              <Button type="primary" icon={<PlusOutlined />} onClick={openAdd}>
                新增学习
              </Button>
            )}
            {can('discipline:edit') && (
              <Button
                icon={<EditOutlined />}
                disabled={!selected}
                onClick={() => selected && openEdit(selected)}
              >
                编辑
              </Button>
            )}
            {can('discipline:list') && (
              <Button
                icon={<TeamOutlined />}
                disabled={!selected}
                onClick={() => selected && openParticipants(selected)}
              >
                参学名单
              </Button>
            )}
            {can('discipline:remove') && (
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
            <Empty description="暂无党纪学习记录" style={{ padding: '40px 0' }} />
          ) : (
            <Table
              rowKey="studyId"
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
                  background: selected?.studyId === record.studyId ? '#FFF5F5' : undefined,
                },
              })}
              columns={[
                { title: '学习主题', dataIndex: 'title', ellipsis: true },
                {
                  title: '学习类型',
                  dataIndex: 'studyType',
                  width: 120,
                  render: (v: number, r) => (
                    <Tag color="red">
                      {r.studyTypeLabel ??
                        DISCIPLINE_TYPE_OPTIONS.find((o) => o.value === v)?.label ??
                        '—'}
                    </Tag>
                  ),
                },
                { title: '党组织', dataIndex: 'orgName', width: 160, ellipsis: true },
                { title: '学习日期', dataIndex: 'studyDate', width: 120 },
                { title: '学习地点', dataIndex: 'place', width: 150, ellipsis: true },
                { title: '主讲人', dataIndex: 'teacher', width: 100 },
                {
                  title: '参学/通过',
                  width: 110,
                  render: (_, r) => `${r.participantCount ?? 0} / ${r.passCount ?? 0}`,
                },
                {
                  title: '状态',
                  dataIndex: 'status',
                  width: 100,
                  render: (v: number, r: DisciplineStudy) =>
                    r.statusLabel ? (
                      <Tag color="blue">{r.statusLabel}</Tag>
                    ) : (
                      <Tag>{v === undefined ? '—' : v}</Tag>
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
                          openParticipants(r);
                        }}
                      >
                        参学名单
                      </Button>
                      {can('discipline:remove') && (
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

      {/* 新增 / 编辑学习记录 */}
      <Modal
        open={modalOpen}
        title={editing ? '编辑党纪学习' : '新增党纪学习'}
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
            label="学习主题"
            rules={[{ required: true, message: '请输入学习主题' }]}
          >
            <Input placeholder="如：《中国共产党纪律处分条例》专题学习" />
          </Form.Item>

          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="studyType" label="学习类型">
                <Select placeholder="请选择" allowClear options={DISCIPLINE_TYPE_OPTIONS} />
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
              <Form.Item name="studyDate" label="学习日期">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="place" label="学习地点">
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="teacher" label="主讲人">
                <Input />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="status" label="状态">
                <Select
                  placeholder="请选择"
                  allowClear
                  options={[
                    { label: '草稿', value: 0 },
                    { label: '进行中', value: 1 },
                    { label: '已结束', value: 2 },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="content" label="学习内容">
            <Input.TextArea rows={4} placeholder="填写学习内容与开展情况" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 参学人员 */}
      <Drawer
        open={drawerOpen}
        title={`参学名单${current ? ` · ${current.title}` : ''}`}
        width={820}
        onClose={() => setDrawerOpen(false)}
        extra={
          <Space>
            <Button icon={<PlusOutlined />} onClick={openPartAdd}>
              添加参学人员
            </Button>
            <Button type="primary" loading={partSaving} onClick={saveParticipants}>
              保存名单
            </Button>
          </Space>
        }
      >
        <Spin spinning={partLoading}>
          {participants.length === 0 && !partLoading ? (
            <Empty description="暂无参学人员" style={{ padding: '40px 0' }} />
          ) : (
            <Table
              rowKey={(r) => String(r.participantId ?? r.personId)}
              dataSource={participants}
              size="middle"
              pagination={false}
              columns={[
                { title: '姓名', dataIndex: 'personName', width: 120 },
                {
                  title: '参学状态',
                  dataIndex: 'attendStatus',
                  width: 110,
                  render: (v?: number) =>
                    v === undefined ? '—' : <Tag>{ATTEND_STATUS_TEXT[v] ?? v}</Tag>,
                },
                {
                  title: '成绩',
                  dataIndex: 'score',
                  width: 90,
                  render: (v?: number) => v ?? '—',
                },
                {
                  title: '是否合格',
                  dataIndex: 'isPassed',
                  width: 100,
                  render: (v?: number) =>
                    v ? <Tag color="success">合格</Tag> : <Tag color="error">不合格</Tag>,
                },
                {
                  title: '操作',
                  width: 130,
                  render: (_, __, index) => (
                    <Space size={0}>
                      <Button type="link" size="small" onClick={() => openPartEdit(index)}>
                        编辑
                      </Button>
                      <Button
                        type="link"
                        size="small"
                        danger
                        onClick={() =>
                          setParticipants((prev) => prev.filter((_, i) => i !== index))
                        }
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

      {/* 参学人员表单 */}
      <Modal
        open={partModalOpen}
        title={partIndex === null ? '添加参学人员' : '编辑参学人员'}
        onCancel={() => setPartModalOpen(false)}
        onOk={submitParticipant}
        okText="确定"
        cancelText="取消"
        destroyOnHidden
      >
        <Form form={partForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="personId"
            label="人员"
            rules={[{ required: true, message: '请选择人员' }]}
          >
            <Select
              placeholder="请选择人员"
              showSearch
              optionFilterProp="label"
              disabled={partIndex !== null}
              options={persons.map((p) => ({
                label: `${p.name}${p.orgName ? `（${p.orgName}）` : ''}`,
                value: p.personId,
              }))}
            />
          </Form.Item>
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item name="attendStatus" label="参学状态">
                <Select placeholder="请选择" allowClear options={DISCIPLINE_ATTEND_OPTIONS} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="isPassed" label="是否合格">
                <Select
                  placeholder="请选择"
                  allowClear
                  options={[
                    { label: '合格', value: 1 },
                    { label: '不合格', value: 0 },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="score" label="成绩">
                <InputNumber min={0} precision={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </div>
  );
}
