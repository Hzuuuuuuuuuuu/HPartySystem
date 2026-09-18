import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  App as AntdApp,
  Button,
  Card,
  Checkbox,
  Divider,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tree,
  Typography,
} from 'antd';
import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import {
  addRole,
  assignRoleMenus,
  changeRoleStatus,
  getRole,
  listMenuTree,
  pageRoles,
  removeRole,
  updateRole,
  type SysMenu,
  type SysRole,
  type SysRoleQuery,
} from '@/api/system';
import { useUserStore } from '@/store/user';

const PAGE_SIZE = 10;

/** 数据范围：1=全部 2=本级 3=本级及以下 4=仅本人 5=自定义 */
const DATA_SCOPE_LABEL: Record<number, string> = {
  1: '全部数据',
  2: '本级数据',
  3: '本级及以下',
  4: '仅本人数据',
  5: '自定义数据',
};

const DATA_SCOPE_OPTIONS = Object.entries(DATA_SCOPE_LABEL).map(([v, l]) => ({
  label: l,
  value: Number(v),
}));

interface MenuTreeNode {
  title: string;
  key: number;
  children?: MenuTreeNode[];
}

/** 菜单树 → antd Tree 数据 */
function toTreeData(nodes: SysMenu[]): MenuTreeNode[] {
  return nodes.map((n) => ({
    title: n.menuName,
    key: n.menuId,
    children: n.children?.length ? toTreeData(n.children) : undefined,
  }));
}

/** 收集所有叶子节点 id（回显时只勾叶子，父节点由 antd 自动半选） */
function collectLeafKeys(nodes: SysMenu[], acc: number[] = []): number[] {
  for (const n of nodes) {
    if (n.children?.length) collectLeafKeys(n.children, acc);
    else acc.push(n.menuId);
  }
  return acc;
}

/** 收集全部节点 id（全选用） */
function collectAllKeys(nodes: SysMenu[], acc: number[] = []): number[] {
  for (const n of nodes) {
    acc.push(n.menuId);
    if (n.children?.length) collectAllKeys(n.children, acc);
  }
  return acc;
}

/** 关键字过滤菜单树：保留命中节点及其祖先 */
function filterTree(nodes: MenuTreeNode[], keyword: string): MenuTreeNode[] {
  const kw = keyword.trim().toLowerCase();
  if (!kw) return nodes;
  const result: MenuTreeNode[] = [];
  for (const node of nodes) {
    const children = node.children ? filterTree(node.children, keyword) : [];
    if (node.title.toLowerCase().includes(kw) || children.length) {
      result.push({ ...node, children: children.length ? children : node.children });
    }
  }
  return result;
}

/**
 * 角色管理。
 *
 * 编辑弹窗内嵌菜单权限树，勾选结果通过 PUT /system/role/{roleId}/menus 全量覆盖。
 */
export default function SystemRolePage() {
  const { message, modal } = AntdApp.useApp();
  const can = useUserStore((s) => s.can);

  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<SysRole[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNum, setPageNum] = useState(1);
  const [selected, setSelected] = useState<SysRole | null>(null);

  const [searchForm] = Form.useForm();
  const [query, setQuery] = useState<SysRoleQuery>({});

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<SysRole | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  // 菜单权限树
  const [menuTree, setMenuTree] = useState<SysMenu[]>([]);
  const [checkedKeys, setCheckedKeys] = useState<number[]>([]);
  const [halfCheckedKeys, setHalfCheckedKeys] = useState<number[]>([]);
  const [expandedKeys, setExpandedKeys] = useState<number[]>([]);
  const [menuKeyword, setMenuKeyword] = useState('');

  const treeData = useMemo(() => toTreeData(menuTree), [menuTree]);
  const filteredTree = useMemo(() => filterTree(treeData, menuKeyword), [treeData, menuKeyword]);
  const allMenuKeys = useMemo(() => collectAllKeys(menuTree), [menuTree]);

  /** 拉取角色分页 */
  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      const res = await pageRoles({ ...query, pageNum, pageSize: PAGE_SIZE });
      setRows(res.records ?? []);
      setTotal(res.total ?? 0);
    } catch {
      setRows([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [query, pageNum]);

  useEffect(() => {
    fetchList();
  }, [fetchList]);

  /** 菜单树只加载一次 */
  useEffect(() => {
    listMenuTree()
      .then((res) => {
        setMenuTree(res ?? []);
        setExpandedKeys(collectAllKeys(res ?? []));
      })
      .catch(() => setMenuTree([]));
  }, []);

  const doSearch = () => {
    const values = searchForm.getFieldsValue();
    setQuery({
      roleName: values.roleName || undefined,
      roleKey: values.roleKey || undefined,
      status: values.status ?? undefined,
    });
    setPageNum(1);
    setSelected(null);
  };

  const doReset = () => {
    searchForm.resetFields();
    setQuery({});
    setPageNum(1);
    setSelected(null);
  };

  /** 新增 */
  const openAdd = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ roleSort: 0, dataScope: 1, status: 1 });
    setCheckedKeys([]);
    setHalfCheckedKeys([]);
    setMenuKeyword('');
    setModalOpen(true);
  };

  /** 编辑：拉详情回显菜单勾选 */
  const openEdit = async (row: SysRole) => {
    setEditing(row);
    form.resetFields();
    setMenuKeyword('');
    setModalOpen(true);
    try {
      const detail = await getRole(row.roleId);
      setEditing(detail);
      form.setFieldsValue(detail);
      const leafKeys = collectLeafKeys(menuTree);
      const leafSet = new Set(leafKeys);
      setCheckedKeys((detail.menuIds ?? []).filter((id) => leafSet.has(id)));
      setHalfCheckedKeys([]);
    } catch {
      form.setFieldsValue(row);
      setCheckedKeys([]);
      setHalfCheckedKeys([]);
    }
  };

  const submit = async () => {
    const values = await form.validateFields();
    // 提交时把半选的父节点一并带上，保证目录权限不丢
    const menuIds = Array.from(new Set([...checkedKeys, ...halfCheckedKeys]));
    setSubmitting(true);
    try {
      if (editing) {
        await updateRole({ ...values, roleId: editing.roleId });
        await assignRoleMenus(editing.roleId, menuIds);
        message.success('修改成功');
      } else {
        const roleId = await addRole(values);
        if (roleId) await assignRoleMenus(roleId, menuIds);
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

  /** 启停切换 */
  const toggleStatus = async (row: SysRole, checked: boolean) => {
    try {
      await changeRoleStatus(row.roleId, checked ? 1 : 0);
      message.success(checked ? '已启用' : '已停用');
      fetchList();
    } catch {
      // 拦截器已提示
    }
  };

  const confirmRemove = (row: SysRole) => {
    modal.confirm({
      title: `确认删除角色「${row.roleName}」？`,
      content: '删除后已分配该角色的用户将失去对应权限。',
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await removeRole(row.roleId);
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
          <Form.Item name="roleName" label="角色名称">
            <Input allowClear placeholder="角色名称" style={{ width: 150 }} onPressEnter={doSearch} />
          </Form.Item>
          <Form.Item name="roleKey" label="权限字符">
            <Input allowClear placeholder="如：admin" style={{ width: 150 }} onPressEnter={doSearch} />
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
              <Button type="primary" icon={<SearchOutlined />} onClick={doSearch}>
                查询
              </Button>
              <Button onClick={doReset}>重置</Button>
            </Space>
          </Form.Item>
        </Form>

        <Space wrap>
          {can('system:role:add') && (
            <Button type="primary" icon={<PlusOutlined />} onClick={openAdd}>
              新增
            </Button>
          )}
          {can('system:role:edit') && (
            <Button icon={<EditOutlined />} disabled={!selected} onClick={() => selected && openEdit(selected)}>
              编辑
            </Button>
          )}
          {can('system:role:remove') && (
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
          {selected && <Tag color="red">已选：{selected.roleName}</Tag>}
        </Space>
      </Card>

      {/* ---------- 表格 ---------- */}
      <Card variant="borderless" styles={{ body: { padding: 16 } }}>
        <Table<SysRole>
          rowKey="roleId"
          loading={loading}
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
              background: selected?.roleId === record.roleId ? '#FFF5F5' : undefined,
            },
          })}
          columns={[
            {
              title: '角色名称',
              dataIndex: 'roleName',
              width: 180,
              render: (v: string, row) => (
                <Space size={6}>
                  <span>{v}</span>
                  {row.isBuiltin === 1 && <Tag color="gold">内置</Tag>}
                </Space>
              ),
            },
            { title: '权限字符', dataIndex: 'roleKey', width: 180 },
            { title: '显示顺序', dataIndex: 'roleSort', width: 100, render: (v: number) => v ?? 0 },
            {
              title: '数据范围',
              dataIndex: 'dataScope',
              width: 130,
              render: (v: number) => DATA_SCOPE_LABEL[v] ?? '—',
            },
            {
              title: '状态',
              dataIndex: 'status',
              width: 100,
              render: (v: number, row) =>
                can('system:role:edit') ? (
                  <Switch
                    size="small"
                    checked={v === 1}
                    checkedChildren="正常"
                    unCheckedChildren="停用"
                    onChange={(checked) => toggleStatus(row, checked)}
                  />
                ) : (
                  <Tag color={v === 1 ? 'success' : 'default'}>{v === 1 ? '正常' : '停用'}</Tag>
                ),
            },
            { title: '备注', dataIndex: 'remark', ellipsis: true, render: (v: string) => v || '—' },
            {
              title: '创建时间',
              dataIndex: 'createTime',
              width: 170,
              render: (v: string) => v || '—',
            },
          ]}
        />
      </Card>

      {/* ---------- 新增 / 编辑弹窗 ---------- */}
      <Modal
        open={modalOpen}
        title={editing ? '编辑角色' : '新增角色'}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        confirmLoading={submitting}
        okText="确定"
        cancelText="取消"
        width={680}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="roleName" label="角色名称" rules={[{ required: true, message: '请输入角色名称' }]}>
            <Input placeholder="如：支部书记" />
          </Form.Item>

          <Form.Item
            name="roleKey"
            label="权限字符"
            rules={[{ required: true, message: '请输入权限字符' }]}
          >
            <Input placeholder="如：branch_secretary" />
          </Form.Item>

          <Form.Item name="roleSort" label="显示顺序">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="dataScope" label="数据范围">
            <Select options={DATA_SCOPE_OPTIONS} />
          </Form.Item>

          <Form.Item name="status" label="状态">
            <Select
              options={[
                { label: '正常', value: 1 },
                { label: '停用', value: 0 },
              ]}
            />
          </Form.Item>

          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} placeholder="选填" maxLength={200} showCount />
          </Form.Item>

          {/* -------- 菜单权限 -------- */}
          <Divider orientation="left" plain style={{ marginTop: 4 }}>
            菜单权限
          </Divider>

          <Space style={{ marginBottom: 8 }} wrap>
            <Input
              allowClear
              size="small"
              prefix={<SearchOutlined />}
              placeholder="搜索菜单"
              style={{ width: 180 }}
              value={menuKeyword}
              onChange={(e) => setMenuKeyword(e.target.value)}
            />
            <Button size="small" onClick={() => setExpandedKeys(allMenuKeys)}>
              展开/折叠
            </Button>
            <Checkbox
              checked={checkedKeys.length > 0 && checkedKeys.length >= allMenuKeys.length}
              indeterminate={checkedKeys.length > 0 && checkedKeys.length < allMenuKeys.length}
              onChange={(e) => {
                setCheckedKeys(e.target.checked ? allMenuKeys : []);
                setHalfCheckedKeys([]);
              }}
            >
              全选/全不选
            </Checkbox>
          </Space>

          <div
            style={{
              maxHeight: 300,
              overflow: 'auto',
              border: '1px solid #F0F0F0',
              borderRadius: 6,
              padding: 8,
            }}
          >
            <Tree
              checkable
              selectable={false}
              treeData={filteredTree}
              checkedKeys={checkedKeys}
              expandedKeys={expandedKeys}
              onExpand={(keys) => setExpandedKeys(keys as number[])}
              onCheck={(keys, info) => {
                setCheckedKeys((Array.isArray(keys) ? keys : keys.checked) as number[]);
                setHalfCheckedKeys(info.halfCheckedKeys as number[]);
              }}
            />
          </div>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            半选的父级菜单会在保存时自动补齐，取消勾选将收回对应页面的访问权限。
          </Typography.Text>
        </Form>
      </Modal>
    </div>
  );
}
