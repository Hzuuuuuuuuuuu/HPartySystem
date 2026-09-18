import { useCallback, useEffect, useMemo, useState } from 'react';
import type { Key } from 'react';
import {
  App as AntdApp,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  TreeSelect,
} from 'antd';
import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  addDept,
  listDepts,
  listPersonOptions,
  removeDept,
  updateDept,
  type PersonOption,
  type SysDept,
} from '@/api/system';
import { useUserStore } from '@/store/user';

/** 组织类型：1=党委 2=党总支 3=党支部 4=党小组 */
const ORG_TYPE_LABEL: Record<number, string> = {
  1: '党委',
  2: '党总支',
  3: '党支部',
  4: '党小组',
};

const ORG_TYPE_COLOR: Record<number, string> = {
  1: 'red',
  2: 'orange',
  3: 'blue',
  4: 'default',
};

const ORG_TYPE_OPTIONS = Object.entries(ORG_TYPE_LABEL).map(([v, l]) => ({
  label: l,
  value: Number(v),
}));

interface TreeNode {
  value: number;
  title: string;
  children?: TreeNode[];
}

/** 平铺组织 → 树（按 orderNum 排序） */
function buildTree(list: SysDept[], parentId = 0): SysDept[] {
  return list
    .filter((d) => (d.parentId ?? 0) === parentId)
    .sort((a, b) => (a.orderNum ?? 0) - (b.orderNum ?? 0))
    .map((d) => {
      const children = buildTree(list, d.orgId);
      return children.length ? { ...d, children } : { ...d };
    });
}

/** 上级组织下拉数据，编辑时排除自身及其子树 */
function toParentTree(nodes: SysDept[], excludeId?: number): TreeNode[] {
  return nodes
    .filter((n) => n.orgId !== excludeId)
    .map((n) => ({
      value: n.orgId,
      title: n.orgName,
      children: n.children?.length ? toParentTree(n.children, excludeId) : undefined,
    }));
}

/**
 * 党组织管理（表格形式）。
 *
 * 与 orginfo/tree.tsx 的架构图互补：这里负责树形表格化的增删改查。
 */
export default function SystemDeptPage() {
  const { message, modal } = AntdApp.useApp();
  const can = useUserStore((s) => s.can);

  const [loading, setLoading] = useState(false);
  const [flatDepts, setFlatDepts] = useState<SysDept[]>([]);
  const [selected, setSelected] = useState<SysDept | null>(null);
  const [expandedKeys, setExpandedKeys] = useState<readonly Key[]>([]);

  const [searchForm] = Form.useForm();
  const [keyword, setKeyword] = useState('');
  const [typeFilter, setTypeFilter] = useState<number | undefined>(undefined);
  const [statusFilter, setStatusFilter] = useState<number | undefined>(undefined);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<SysDept | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const [persons, setPersons] = useState<PersonOption[]>([]);

  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      const list = await listDepts();
      setFlatDepts(list);
      setExpandedKeys(list.map((d) => d.orgId));
    } catch {
      setFlatDepts([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchList();
    listPersonOptions()
      .then(setPersons)
      .catch(() => setPersons([]));
  }, [fetchList]);

  /** 客户端过滤：命中节点的祖先一并保留 */
  const treeData = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    let list = flatDepts;
    if (kw || typeFilter !== undefined || statusFilter !== undefined) {
      const byId = new Map(flatDepts.map((d) => [d.orgId, d]));
      const keep = new Set<number>();
      for (const d of flatDepts) {
        const nameHit = !kw || d.orgName.toLowerCase().includes(kw);
        const typeHit = typeFilter === undefined || d.orgType === typeFilter;
        const statusHit = statusFilter === undefined || d.status === statusFilter;
        if (!nameHit || !typeHit || !statusHit) continue;
        keep.add(d.orgId);
        let parent = d.parentId ?? 0;
        while (parent && byId.has(parent)) {
          keep.add(parent);
          parent = byId.get(parent)?.parentId ?? 0;
        }
      }
      list = flatDepts.filter((d) => keep.has(d.orgId));
    }
    return buildTree(list);
  }, [flatDepts, keyword, typeFilter, statusFilter]);

  const openAdd = (parentId = 0) => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ parentId, orgType: 3, orgLevel: 3, orderNum: 0, status: 1 });
    setModalOpen(true);
  };

  const openEdit = (row: SysDept) => {
    setEditing(row);
    form.resetFields();
    form.setFieldsValue({
      ...row,
      foundedDate: row.foundedDate ? dayjs(row.foundedDate) : undefined,
    });
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    const payload = {
      ...values,
      foundedDate: values.foundedDate ? values.foundedDate.format('YYYY-MM-DD') : undefined,
    };
    setSubmitting(true);
    try {
      if (editing) {
        await updateDept({ ...payload, orgId: editing.orgId });
        message.success('修改成功');
      } else {
        await addDept(payload);
        message.success('新增成功');
      }
      setModalOpen(false);
      fetchList();
    } catch {
      // 错误提示由拦截器统一处理
    } finally {
      setSubmitting(false);
    }
  };

  const confirmRemove = (row: SysDept) => {
    modal.confirm({
      title: `确认删除「${row.orgName}」？`,
      content: '存在下级组织或关联人员时无法删除。',
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await removeDept(row.orgId);
        message.success('删除成功');
        setSelected(null);
        fetchList();
      },
    });
  };

  return (
    <div>
      {/* ---------- 工具栏 ---------- */}
      <Card variant="borderless" style={{ marginBottom: 12 }} styles={{ body: { padding: 16 } }}>
        <Form form={searchForm} layout="inline" style={{ marginBottom: 12, rowGap: 12 }}>
          <Form.Item name="orgName" label="组织名称">
            <Input
              allowClear
              placeholder="组织名称"
              style={{ width: 180 }}
              onPressEnter={(e) => setKeyword((e.target as HTMLInputElement).value)}
            />
          </Form.Item>
          <Form.Item name="orgType" label="组织类型">
            <Select allowClear placeholder="全部" style={{ width: 130 }} options={ORG_TYPE_OPTIONS} />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select
              allowClear
              placeholder="全部"
              style={{ width: 110 }}
              options={[
                { label: '正常', value: 1 },
                { label: '停用', value: 0 },
              ]}
            />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button
                type="primary"
                icon={<SearchOutlined />}
                onClick={() => {
                  const v = searchForm.getFieldsValue();
                  setKeyword(v.orgName ?? '');
                  setTypeFilter(v.orgType ?? undefined);
                  setStatusFilter(v.status ?? undefined);
                  setSelected(null);
                }}
              >
                查询
              </Button>
              <Button
                onClick={() => {
                  searchForm.resetFields();
                  setKeyword('');
                  setTypeFilter(undefined);
                  setStatusFilter(undefined);
                  setSelected(null);
                }}
              >
                重置
              </Button>
            </Space>
          </Form.Item>
        </Form>

        <Space wrap>
          {can('system:dept:add') && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => openAdd(0)}>
              新增
            </Button>
          )}
          {can('system:dept:edit') && (
            <Button icon={<EditOutlined />} disabled={!selected} onClick={() => selected && openEdit(selected)}>
              编辑
            </Button>
          )}
          {can('system:dept:remove') && (
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
          {selected && <Tag color="red">已选：{selected.orgName}</Tag>}
        </Space>
      </Card>

      {/* ---------- 树形表格 ---------- */}
      <Card variant="borderless" styles={{ body: { padding: 16 } }}>
        <Table<SysDept>
          rowKey="orgId"
          loading={loading}
          dataSource={treeData}
          size="middle"
          pagination={false}
          scroll={{ x: 1200 }}
          expandable={{
            expandedRowKeys: expandedKeys,
            onExpandedRowsChange: setExpandedKeys,
          }}
          onRow={(record) => ({
            onClick: () => setSelected(record),
            style: {
              cursor: 'pointer',
              background: selected?.orgId === record.orgId ? '#FFF5F5' : undefined,
            },
          })}
          columns={[
            { title: '组织名称', dataIndex: 'orgName', width: 240 },
            {
              title: '组织类型',
              dataIndex: 'orgType',
              width: 110,
              render: (v: number) => (
                <Tag color={ORG_TYPE_COLOR[v] ?? 'default'}>{ORG_TYPE_LABEL[v] ?? '—'}</Tag>
              ),
            },
            {
              title: '书记',
              dataIndex: 'leader',
              width: 110,
              render: (v: string, row) => v || row.secretaryName || '—',
            },
            {
              title: '党员数',
              dataIndex: 'memberCount',
              width: 90,
              render: (v: number) => v ?? 0,
            },
            {
              title: '成立日期',
              dataIndex: 'foundedDate',
              width: 130,
              render: (v: string) => v || '—',
            },
            { title: '联系电话', dataIndex: 'phone', width: 140, render: (v: string) => v || '—' },
            { title: '办公地址', dataIndex: 'address', ellipsis: true, render: (v: string) => v || '—' },
            { title: '排序', dataIndex: 'orderNum', width: 80, render: (v: number) => v ?? 0 },
            {
              title: '状态',
              dataIndex: 'status',
              width: 90,
              render: (v: number) => (
                <Tag color={v === 0 ? 'default' : 'success'}>{v === 0 ? '停用' : '正常'}</Tag>
              ),
            },
            {
              title: '操作',
              key: 'action',
              width: 200,
              fixed: 'right',
              render: (_: unknown, row) => (
                <Space size={4}>
                  {can('system:dept:add') && row.orgType !== 4 && (
                    <Button type="link" size="small" onClick={() => openAdd(row.orgId)}>
                      新增下级
                    </Button>
                  )}
                  {can('system:dept:edit') && (
                    <Button type="link" size="small" onClick={() => openEdit(row)}>
                      编辑
                    </Button>
                  )}
                  {can('system:dept:remove') && (
                    <Button type="link" size="small" danger onClick={() => confirmRemove(row)}>
                      删除
                    </Button>
                  )}
                </Space>
              ),
            },
          ]}
        />
      </Card>

      {/* ---------- 新增 / 编辑弹窗 ---------- */}
      <Modal
        open={modalOpen}
        title={editing ? '编辑党组织' : '新增党组织'}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        confirmLoading={submitting}
        okText="确定"
        cancelText="取消"
        width={640}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="parentId" label="上级组织">
            <TreeSelect
              showSearch
              treeNodeFilterProp="title"
              placeholder="不选则作为根组织"
              treeData={toParentTree(buildTree(flatDepts), editing?.orgId)}
            />
          </Form.Item>

          <Form.Item name="orgName" label="组织名称" rules={[{ required: true, message: '请输入组织名称' }]}>
            <Input placeholder="如：第一支部" />
          </Form.Item>

          <Form.Item name="orgShortName" label="组织简称">
            <Input placeholder="选填" />
          </Form.Item>

          <Form.Item name="orgCode" label="组织编码">
            <Input placeholder="选填，如：DW001" />
          </Form.Item>

          <Form.Item name="orgType" label="组织类型" rules={[{ required: true, message: '请选择组织类型' }]}>
            <Select
              options={ORG_TYPE_OPTIONS}
              onChange={(v: number) => form.setFieldValue('orgLevel', v)}
            />
          </Form.Item>

          <Form.Item name="orgLevel" label="组织层级">
            <Select
              options={[
                { label: '1 级', value: 1 },
                { label: '2 级', value: 2 },
                { label: '3 级', value: 3 },
                { label: '4 级', value: 4 },
              ]}
            />
          </Form.Item>

          <Form.Item name="secretaryId" label="书记">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="从党员名册中选择"
              options={persons.map((p) => ({
                label: `${p.name}${p.orgName ? `（${p.orgName}）` : ''}`,
                value: p.personId,
              }))}
            />
          </Form.Item>

          <Form.Item name="leader" label="负责人姓名">
            <Input placeholder="选填，用于架构图展示" />
          </Form.Item>

          <Form.Item name="phone" label="联系电话">
            <Input />
          </Form.Item>

          <Form.Item name="address" label="办公地址">
            <Input />
          </Form.Item>

          <Form.Item name="memberCount" label="党员数">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="foundedDate" label="成立日期">
            <DatePicker style={{ width: '100%' }} placeholder="选择日期" />
          </Form.Item>

          <Form.Item name="orderNum" label="显示排序">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="status" label="状态">
            <Select
              options={[
                { label: '正常', value: 1 },
                { label: '停用', value: 0 },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
