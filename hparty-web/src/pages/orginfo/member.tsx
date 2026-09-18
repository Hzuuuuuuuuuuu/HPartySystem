import { useCallback, useEffect, useState } from 'react';
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Segmented,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  TreeSelect,
  Typography,
} from 'antd';
import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  UserOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  addPerson,
  getPerson,
  getPersonStatistics,
  listDeptTree,
  pageMembers,
  pagePersons,
  removePerson,
  updatePerson,
  type PartyPerson,
  type PartyPersonQuery,
  type SysDept,
} from '@/api/system';
import { exportMember } from '@/api/report';
import { useUserStore } from '@/store/user';

const PAGE_SIZE = 10;

/** 党员状态：0=群众 1=入党申请人 2=入党积极分子 3=发展对象 4=预备党员 5=正式党员 6=流动党员 */
const MEMBER_STATUS_LABEL: Record<number, string> = {
  0: '群众',
  1: '入党申请人',
  2: '入党积极分子',
  3: '发展对象',
  4: '预备党员',
  5: '正式党员',
  6: '流动党员',
};

const MEMBER_STATUS_COLOR: Record<number, string> = {
  0: 'default',
  1: 'cyan',
  2: 'blue',
  3: 'purple',
  4: 'orange',
  5: 'red',
  6: 'geekblue',
};

/** 统计接口返回结构，字段名以后端为准，全部可选 + 容错 */
interface PersonStat {
  total?: number;
  memberCount?: number;
  fullMemberCount?: number;
  probationaryCount?: number;
  flowingCount?: number;
  applicantCount?: number;
  activistCount?: number;
  candidateCount?: number;
  massCount?: number;
}

/** 数值容错，接口字段缺失时按 0 展示 */
function num(value: unknown): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}

interface OrgTreeNode {
  value: number;
  title: string;
  children?: OrgTreeNode[];
}

function toOrgTree(nodes: SysDept[]): OrgTreeNode[] {
  return nodes.map((n) => ({
    value: n.orgId,
    title: n.orgName,
    children: n.children?.length ? toOrgTree(n.children) : undefined,
  }));
}

/** 日期字段 → dayjs */
function toDay(value?: string) {
  return value ? dayjs(value) : undefined;
}

/** 党员名册 */
export default function OrgMemberPage() {
  const { message, modal } = AntdApp.useApp();
  const can = useUserStore((s) => s.can);

  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<PartyPerson[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNum, setPageNum] = useState(1);
  /** '' = 全部，'member' = 仅党员，其余为 memberStatus 数值字符串 */
  const [statusFilter, setStatusFilter] = useState('');
  const [selected, setSelected] = useState<PartyPerson | null>(null);

  const [searchForm] = Form.useForm();
  const [query, setQuery] = useState<PartyPersonQuery>({});
  const [orgTree, setOrgTree] = useState<OrgTreeNode[]>([]);

  const [stat, setStat] = useState<PersonStat>({});

  // 详情抽屉
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<PartyPerson | null>(null);

  // 新增 / 编辑
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<PartyPerson | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      const memberStatus = statusFilter && statusFilter !== 'member' ? Number(statusFilter) : undefined;
      const params = { ...query, memberStatus, pageNum, pageSize: PAGE_SIZE };
      const res = statusFilter === 'member' ? await pageMembers(params) : await pagePersons(params);
      setRows(res.records ?? []);
      setTotal(res.total ?? 0);
    } catch {
      setRows([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [query, pageNum, statusFilter]);

  useEffect(() => {
    fetchList();
  }, [fetchList]);

  const fetchStat = useCallback(async () => {
    try {
      const data = (await getPersonStatistics()) as unknown as PersonStat;
      setStat(data ?? {});
    } catch {
      setStat({});
    }
  }, []);

  useEffect(() => {
    fetchStat();
    listDeptTree()
      .then((res) => setOrgTree(toOrgTree(res ?? [])))
      .catch(() => setOrgTree([]));
  }, [fetchStat]);

  const doSearch = () => {
    const values = searchForm.getFieldsValue();
    setQuery({
      name: values.name || undefined,
      phone: values.phone || undefined,
      orgId: values.orgId ?? undefined,
    });
    setPageNum(1);
    setSelected(null);
  };

  const doReset = () => {
    searchForm.resetFields();
    setQuery({});
    setStatusFilter('');
    setPageNum(1);
    setSelected(null);
  };

  /** 打开详情抽屉 */
  const openDetail = async (row: PartyPerson) => {
    setDrawerOpen(true);
    setDetail(row);
    setDetailLoading(true);
    try {
      setDetail(await getPerson(row.personId));
    } catch {
      // 保留列表行数据
    } finally {
      setDetailLoading(false);
    }
  };

  const openAdd = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({
      sex: 1,
      memberStatus: 0,
      isMember: 0,
      orgId: query.orgId ?? undefined,
    });
    setModalOpen(true);
  };

  const openEdit = async (row: PartyPerson) => {
    setEditing(row);
    form.resetFields();
    setModalOpen(true);
    let data: PartyPerson = row;
    try {
      data = await getPerson(row.personId);
      setEditing(data);
    } catch {
      // 退回列表行数据
    }
    form.setFieldsValue({
      ...data,
      birthDate: toDay(data.birthDate),
      applyDate: toDay(data.applyDate),
      activistDate: toDay(data.activistDate),
      candidateDate: toDay(data.candidateDate),
      probationaryDate: toDay(data.probationaryDate),
      fullMemberDate: toDay(data.fullMemberDate),
    });
  };

  const submit = async () => {
    const values = await form.validateFields();
    const payload = {
      ...values,
      birthDate: values.birthDate ? values.birthDate.format('YYYY-MM-DD') : undefined,
      applyDate: values.applyDate ? values.applyDate.format('YYYY-MM-DD') : undefined,
      activistDate: values.activistDate ? values.activistDate.format('YYYY-MM-DD') : undefined,
      candidateDate: values.candidateDate ? values.candidateDate.format('YYYY-MM-DD') : undefined,
      probationaryDate: values.probationaryDate ? values.probationaryDate.format('YYYY-MM-DD') : undefined,
      fullMemberDate: values.fullMemberDate ? values.fullMemberDate.format('YYYY-MM-DD') : undefined,
    };
    setSubmitting(true);
    try {
      if (editing) {
        await updatePerson({ ...payload, personId: editing.personId });
        message.success('修改成功');
      } else {
        await addPerson(payload);
        message.success('新增成功');
      }
      setModalOpen(false);
      fetchList();
      fetchStat();
    } catch {
      // 错误提示由拦截器统一处理
    } finally {
      setSubmitting(false);
    }
  };

  const confirmRemove = (row: PartyPerson) => {
    modal.confirm({
      title: `确认删除「${row.name}」的档案？`,
      content: '存在发展党员流程记录时无法删除。',
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await removePerson(row.personId);
        message.success('删除成功');
        setSelected(null);
        fetchList();
        fetchStat();
      },
    });
  };

  /** 状态筛选选项 */
  const segmentedOptions = [
    { label: '全部', value: '' },
    { label: '仅党员', value: 'member' },
    ...Object.entries(MEMBER_STATUS_LABEL).map(([v, l]) => ({ label: l, value: v })),
  ];

  return (
    <div>
      {/* ---------- 统计卡片 ---------- */}
      <Card variant="borderless" style={{ marginBottom: 12 }} styles={{ body: { padding: 16 } }}>
        <Row gutter={[16, 16]}>
          <Col xs={12} sm={8} lg={4}>
            <Statistic title="人员总数" value={num(stat.total)} prefix={<UserOutlined />} />
          </Col>
          <Col xs={12} sm={8} lg={4}>
            <Statistic title="党员总数" value={num(stat.memberCount)} valueStyle={{ color: '#C7000B' }} />
          </Col>
          <Col xs={12} sm={8} lg={4}>
            <Statistic title="正式党员" value={num(stat.fullMemberCount)} />
          </Col>
          <Col xs={12} sm={8} lg={4}>
            <Statistic title="预备党员" value={num(stat.probationaryCount)} />
          </Col>
          <Col xs={12} sm={8} lg={4}>
            <Statistic title="流动党员" value={num(stat.flowingCount)} />
          </Col>
          <Col xs={12} sm={8} lg={4}>
            <Statistic title="入党申请人" value={num(stat.applicantCount)} />
          </Col>
        </Row>
      </Card>

      {/* ---------- 工具栏 ---------- */}
      <Card variant="borderless" style={{ marginBottom: 12 }} styles={{ body: { padding: 16 } }}>
        <Segmented
          options={segmentedOptions}
          value={statusFilter}
          onChange={(v) => {
            setStatusFilter(String(v));
            setPageNum(1);
            setSelected(null);
          }}
          style={{ marginBottom: 12 }}
        />

        <Form form={searchForm} layout="inline" style={{ marginBottom: 12, rowGap: 12 }}>
          <Form.Item name="name" label="姓名">
            <Input allowClear placeholder="姓名" style={{ width: 130 }} onPressEnter={doSearch} />
          </Form.Item>
          <Form.Item name="phone" label="手机号">
            <Input allowClear placeholder="手机号码" style={{ width: 150 }} onPressEnter={doSearch} />
          </Form.Item>
          <Form.Item name="orgId" label="所属组织">
            <TreeSelect
              allowClear
              showSearch
              treeNodeFilterProp="title"
              placeholder="全部组织"
              treeData={orgTree}
              style={{ width: 220 }}
            />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" icon={<SearchOutlined />} onClick={doSearch}>
                查询
              </Button>
              <Button onClick={doReset}>重置</Button>
            </Space>
          </Form.Item>
        </Form>

        <Space wrap>
          {can('system:person:add') && (
            <Button type="primary" icon={<PlusOutlined />} onClick={openAdd}>
              新增
            </Button>
          )}
          {can('system:person:edit') && (
            <Button icon={<EditOutlined />} disabled={!selected} onClick={() => selected && openEdit(selected)}>
              编辑
            </Button>
          )}
          {can('system:person:remove') && (
            <Button
              icon={<DeleteOutlined />}
              disabled={!selected}
              onClick={() => selected && confirmRemove(selected)}
            >
              删除
            </Button>
          )}
          <Button icon={<ReloadOutlined />} onClick={fetchList}>
            刷新
          </Button>
          {can('report:export') && (
            <Button
              icon={<DownloadOutlined />}
              onClick={async () => {
                try {
                  const memberStatus =
                    statusFilter && statusFilter !== 'member' ? Number(statusFilter) : undefined;
                  await exportMember({ ...query, memberStatus });
                  message.success('导出已开始');
                } catch {
                  // 错误提示由拦截器统一处理
                }
              }}
            >
              导出党员名册
            </Button>
          )}
          {selected && <Tag color="red">已选：{selected.name}</Tag>}
        </Space>
      </Card>

      {/* ---------- 表格 ---------- */}
      <Card variant="borderless" styles={{ body: { padding: 16 } }}>
        <Table<PartyPerson>
          rowKey="personId"
          loading={loading}
          dataSource={rows}
          size="middle"
          scroll={{ x: 1400 }}
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
            onDoubleClick: () => openDetail(record),
            style: {
              cursor: 'pointer',
              background: selected?.personId === record.personId ? '#FFF5F5' : undefined,
            },
          })}
          columns={[
            { title: '姓名', dataIndex: 'name', width: 110, fixed: 'left' },
            {
              title: '性别',
              dataIndex: 'sexLabel',
              width: 70,
              render: (v: string, row) => v || (row.sex === 1 ? '男' : row.sex === 2 ? '女' : '—'),
            },
            { title: '年龄', dataIndex: 'age', width: 70, render: (v: number) => v ?? '—' },
            { title: '民族', dataIndex: 'nation', width: 90, render: (v: string) => v || '—' },
            { title: '手机号', dataIndex: 'phone', width: 130, render: (v: string) => v || '—' },
            { title: '学历', dataIndex: 'education', width: 100, render: (v: string) => v || '—' },
            { title: '工作单位', dataIndex: 'workUnit', width: 180, ellipsis: true, render: (v: string) => v || '—' },
            { title: '所属组织', dataIndex: 'orgName', width: 180, ellipsis: true, render: (v: string) => v || '—' },
            {
              title: '党员状态',
              dataIndex: 'memberStatusLabel',
              width: 120,
              render: (v: string, row) => (
                <Tag color={MEMBER_STATUS_COLOR[row.memberStatus ?? 0] ?? 'default'}>
                  {v || MEMBER_STATUS_LABEL[row.memberStatus ?? 0] || '—'}
                </Tag>
              ),
            },
            {
              title: '入党时间',
              dataIndex: 'fullMemberDate',
              width: 130,
              render: (v: string) => v || '—',
            },
            { title: '党龄', dataIndex: 'partyAge', width: 80, render: (v: number) => (v == null ? '—' : `${v} 年`) },
            {
              title: '操作',
              key: 'action',
              width: 90,
              fixed: 'right',
              render: (_: unknown, row) => (
                <Button type="link" size="small" onClick={() => openDetail(row)}>
                  详情
                </Button>
              ),
            },
          ]}
        />
      </Card>

      {/* ---------- 详情抽屉 ---------- */}
      <Drawer
        open={drawerOpen}
        title="党员档案详情"
        width={680}
        onClose={() => setDrawerOpen(false)}
        destroyOnHidden
      >
        <Spin spinning={detailLoading}>
          {detail ? (
            <Descriptions bordered size="small" column={2}>
              <Descriptions.Item label="姓名">{detail.name}</Descriptions.Item>
              <Descriptions.Item label="人员编号">{detail.personNo || '—'}</Descriptions.Item>
              <Descriptions.Item label="性别">
                {detail.sexLabel || (detail.sex === 1 ? '男' : detail.sex === 2 ? '女' : '—')}
              </Descriptions.Item>
              <Descriptions.Item label="年龄">{detail.age ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="身份证号" span={2}>
                {detail.idCard || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="出生日期">{detail.birthDate || '—'}</Descriptions.Item>
              <Descriptions.Item label="民族">{detail.nation || '—'}</Descriptions.Item>
              <Descriptions.Item label="籍贯" span={2}>
                {detail.nativePlace || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="手机号">{detail.phone || '—'}</Descriptions.Item>
              <Descriptions.Item label="学历">{detail.education || '—'}</Descriptions.Item>
              <Descriptions.Item label="工作单位" span={2}>
                {detail.workUnit || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="职务">{detail.jobTitle || '—'}</Descriptions.Item>
              <Descriptions.Item label="所属组织">{detail.orgName || '—'}</Descriptions.Item>
              <Descriptions.Item label="党员状态">
                <Tag color={MEMBER_STATUS_COLOR[detail.memberStatus ?? 0] ?? 'default'}>
                  {detail.memberStatusLabel || MEMBER_STATUS_LABEL[detail.memberStatus ?? 0] || '—'}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="政治面貌">{detail.politicalStatus || '—'}</Descriptions.Item>
              <Descriptions.Item label="递交申请书">{detail.applyDate || '—'}</Descriptions.Item>
              <Descriptions.Item label="确定为积极分子">{detail.activistDate || '—'}</Descriptions.Item>
              <Descriptions.Item label="确定为发展对象">{detail.candidateDate || '—'}</Descriptions.Item>
              <Descriptions.Item label="入党日期（预备）">{detail.probationaryDate || '—'}</Descriptions.Item>
              <Descriptions.Item label="转正日期">{detail.fullMemberDate || '—'}</Descriptions.Item>
              <Descriptions.Item label="党龄">
                {detail.partyAge == null ? '—' : `${detail.partyAge} 年`}
              </Descriptions.Item>
            </Descriptions>
          ) : (
            <Typography.Text type="secondary">暂无数据</Typography.Text>
          )}
        </Spin>
      </Drawer>

      {/* ---------- 新增 / 编辑弹窗 ---------- */}
      <Modal
        open={modalOpen}
        title={editing ? '编辑人员档案' : '新增人员档案'}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        confirmLoading={submitting}
        okText="确定"
        cancelText="取消"
        width={760}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="name" label="姓名" rules={[{ required: true, message: '请输入姓名' }]}>
                <Input placeholder="真实姓名" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="personNo" label="人员编号">
                <Input placeholder="选填" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="sex" label="性别">
                <Select
                  options={[
                    { label: '男', value: 1 },
                    { label: '女', value: 2 },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="idCard" label="身份证号">
                <Input placeholder="18 位身份证号" maxLength={18} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="birthDate" label="出生日期">
                <DatePicker style={{ width: '100%' }} placeholder="选择日期" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="age" label="年龄">
                <InputNumber min={0} max={150} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="nation" label="民族">
                <Input placeholder="如：汉族" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="nativePlace" label="籍贯">
                <Input placeholder="如：山东济南" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="phone" label="手机号">
                <Input placeholder="11 位手机号" maxLength={11} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="education" label="学历">
                <Select
                  allowClear
                  placeholder="请选择"
                  options={[
                    '初中及以下',
                    '高中',
                    '中专',
                    '大专',
                    '本科',
                    '硕士',
                    '博士',
                  ].map((v) => ({ label: v, value: v }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="workUnit" label="工作单位">
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="jobTitle" label="职务">
                <Input />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="orgId" label="所属组织" rules={[{ required: true, message: '请选择所属组织' }]}>
                <TreeSelect
                  showSearch
                  treeNodeFilterProp="title"
                  placeholder="请选择党组织"
                  treeData={orgTree}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="memberStatus" label="党员状态">
                <Select
                  options={Object.entries(MEMBER_STATUS_LABEL).map(([v, l]) => ({
                    label: l,
                    value: Number(v),
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="politicalStatus" label="政治面貌">
                <Input placeholder="如：中共党员" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="isMember" label="是否党员">
                <Select
                  options={[
                    { label: '是', value: 1 },
                    { label: '否', value: 0 },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="applyDate" label="递交入党申请书日期">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="activistDate" label="确定为积极分子日期">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="candidateDate" label="确定为发展对象日期">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="probationaryDate" label="入党日期（预备党员）">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="fullMemberDate" label="转正日期">
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="partyAge" label="党龄（年）">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </div>
  );
}
