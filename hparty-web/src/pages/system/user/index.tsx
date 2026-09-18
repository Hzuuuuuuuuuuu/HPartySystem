import { useCallback, useEffect, useState } from 'react';
import {
  App as AntdApp,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  TreeSelect,
} from 'antd';
import {
  DeleteOutlined,
  EditOutlined,
  KeyOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import {
  addUser,
  changeUserStatus,
  getUser,
  listDeptTree,
  listPersonOptions,
  listRoleOptions,
  pageUsers,
  removeUser,
  resetUserPassword,
  updateUser,
  type PersonOption,
  type SysDept,
  type SysRole,
  type SysUser,
  type SysUserQuery,
} from '@/api/system';
import { useUserStore } from '@/store/user';

const PAGE_SIZE = 10;

/** 性别：0=未知 1=男 2=女 */
const SEX_OPTIONS = [
  { label: '未知', value: 0 },
  { label: '男', value: 1 },
  { label: '女', value: 2 },
];

interface OrgTreeNode {
  value: number;
  title: string;
  children?: OrgTreeNode[];
}

/** 组织树 → TreeSelect 数据 */
function toOrgTree(nodes: SysDept[]): OrgTreeNode[] {
  return nodes.map((n) => ({
    value: n.orgId,
    title: n.orgName,
    children: n.children?.length ? toOrgTree(n.children) : undefined,
  }));
}

/** 角色名称集合 → 展示文本（后端返回数组，兼容字符串） */
function roleNamesText(roleNames?: string[] | string) {
  if (!roleNames) return '—';
  if (Array.isArray(roleNames)) return roleNames.length ? roleNames.join('、') : '—';
  return roleNames;
}

/**
 * 用户管理。
 *
 * 左侧组织树通过 TreeSelect 参与筛选与归属设置，角色支持多选分配。
 */
export default function SystemUserPage() {
  const { message, modal } = AntdApp.useApp();
  const can = useUserStore((s) => s.can);

  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<SysUser[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNum, setPageNum] = useState(1);
  const [selected, setSelected] = useState<SysUser | null>(null);

  const [searchForm] = Form.useForm();
  const [query, setQuery] = useState<SysUserQuery>({});

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<SysUser | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const [pwdOpen, setPwdOpen] = useState(false);
  const [pwdTarget, setPwdTarget] = useState<SysUser | null>(null);
  const [pwdForm] = Form.useForm();

  const [roles, setRoles] = useState<SysRole[]>([]);
  const [persons, setPersons] = useState<PersonOption[]>([]);
  const [orgTree, setOrgTree] = useState<OrgTreeNode[]>([]);

  /** 拉取用户分页 */
  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      const res = await pageUsers({ ...query, pageNum, pageSize: PAGE_SIZE });
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

  /** 下拉数据只加载一次 */
  useEffect(() => {
    listRoleOptions()
      .then(setRoles)
      .catch(() => setRoles([]));
    listPersonOptions()
      .then(setPersons)
      .catch(() => setPersons([]));
    listDeptTree()
      .then((res) => setOrgTree(toOrgTree(res ?? [])))
      .catch(() => setOrgTree([]));
  }, []);

  const doSearch = () => {
    const values = searchForm.getFieldsValue();
    setQuery({
      username: values.username || undefined,
      nickName: values.nickName || undefined,
      phone: values.phone || undefined,
      orgId: values.orgId ?? undefined,
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
    form.setFieldsValue({ sex: 1, status: 1, orgId: query.orgId ?? undefined });
    setModalOpen(true);
  };

  /** 编辑：先拉详情，保证 roleIds 完整 */
  const openEdit = async (row: SysUser) => {
    setEditing(row);
    form.resetFields();
    setModalOpen(true);
    try {
      const detail = await getUser(row.userId);
      setEditing(detail);
      form.setFieldsValue({ ...detail, password: undefined });
    } catch {
      form.setFieldsValue({ ...row, password: undefined });
    }
  };

  const submit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      if (editing) {
        await updateUser({ ...values, userId: editing.userId });
        message.success('修改成功');
      } else {
        await addUser(values);
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

  /** 打开重置密码弹窗 */
  const openPwd = (row: SysUser) => {
    setPwdTarget(row);
    pwdForm.resetFields();
    setPwdOpen(true);
  };

  const submitPwd = async () => {
    const values = await pwdForm.validateFields();
    if (!pwdTarget) return;
    try {
      await resetUserPassword(pwdTarget.userId, values.password);
      message.success('密码重置成功');
      setPwdOpen(false);
    } catch {
      // 拦截器已提示
    }
  };

  /** 启停切换 */
  const toggleStatus = async (row: SysUser, checked: boolean) => {
    try {
      await changeUserStatus(row.userId, checked ? 1 : 0);
      message.success(checked ? '已启用' : '已停用');
      fetchList();
    } catch {
      // 拦截器已提示
    }
  };

  const confirmRemove = (row: SysUser) => {
    modal.confirm({
      title: `确认删除用户「${row.username}」？`,
      content: '删除后该账号将无法登录，操作不可恢复。',
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await removeUser(row.userId);
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
          <Form.Item name="username" label="用户名">
            <Input allowClear placeholder="登录账号" style={{ width: 140 }} onPressEnter={doSearch} />
          </Form.Item>
          <Form.Item name="nickName" label="昵称">
            <Input allowClear placeholder="姓名昵称" style={{ width: 140 }} onPressEnter={doSearch} />
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
              style={{ width: 200 }}
            />
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
          {can('system:user:add') && (
            <Button type="primary" icon={<PlusOutlined />} onClick={openAdd}>
              新增
            </Button>
          )}
          {can('system:user:edit') && (
            <Button icon={<EditOutlined />} disabled={!selected} onClick={() => selected && openEdit(selected)}>
              编辑
            </Button>
          )}
          {can('system:user:resetPwd') && (
            <Button icon={<KeyOutlined />} disabled={!selected} onClick={() => selected && openPwd(selected)}>
              重置密码
            </Button>
          )}
          {can('system:user:remove') && (
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
          {selected && <Tag color="red">已选：{selected.username}</Tag>}
        </Space>
      </Card>

      {/* ---------- 表格 ---------- */}
      <Card variant="borderless" styles={{ body: { padding: 16 } }}>
        <Table<SysUser>
          rowKey="userId"
          loading={loading}
          dataSource={rows}
          size="middle"
          scroll={{ x: 1080 }}
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
              background: selected?.userId === record.userId ? '#FFF5F5' : undefined,
            },
          })}
          columns={[
            { title: '用户名', dataIndex: 'username', width: 140 },
            { title: '昵称', dataIndex: 'nickName', width: 120, render: (v: string) => v || '—' },
            { title: '所属组织', dataIndex: 'orgName', width: 180, render: (v: string) => v || '—' },
            { title: '手机号', dataIndex: 'phone', width: 130, render: (v: string) => v || '—' },
            {
              title: '角色',
              dataIndex: 'roleNames',
              width: 200,
              render: (_: unknown, row) => roleNamesText(row.roleNames),
            },
            {
              title: '状态',
              dataIndex: 'status',
              width: 100,
              render: (v: number, row) =>
                can('system:user:edit') ? (
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
            {
              title: '最后登录',
              dataIndex: 'loginDate',
              width: 170,
              render: (v: string) => v || '—',
            },
            {
              title: '最后登录IP',
              dataIndex: 'loginIp',
              width: 140,
              render: (v: string) => v || '—',
            },
          ]}
        />
      </Card>

      {/* ---------- 新增 / 编辑弹窗 ---------- */}
      <Modal
        open={modalOpen}
        title={editing ? '编辑用户' : '新增用户'}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        confirmLoading={submitting}
        okText="确定"
        cancelText="取消"
        width={640}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="username"
            label="登录账号"
            rules={[{ required: true, message: '请输入登录账号' }]}
          >
            <Input placeholder="如：zhangsan" disabled={!!editing} />
          </Form.Item>

          {!editing && (
            <Form.Item
              name="password"
              label="初始密码"
              rules={[
                { required: true, message: '请输入初始密码' },
                { min: 6, message: '密码不少于 6 位' },
              ]}
            >
              <Input.Password placeholder="不少于 6 位" autoComplete="new-password" />
            </Form.Item>
          )}

          <Form.Item name="nickName" label="昵称" rules={[{ required: true, message: '请输入昵称' }]}>
            <Input placeholder="如：张三" />
          </Form.Item>

          <Form.Item name="personId" label="关联人员档案">
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="选填，关联后可同步党员信息"
              options={persons.map((p) => ({
                label: `${p.name}${p.orgName ? `（${p.orgName}）` : ''}`,
                value: p.personId,
              }))}
            />
          </Form.Item>

          <Form.Item name="orgId" label="所属组织">
            <TreeSelect
              allowClear
              showSearch
              treeNodeFilterProp="title"
              placeholder="请选择所属党组织"
              treeData={orgTree}
            />
          </Form.Item>

          <Form.Item name="roleIds" label="分配角色">
            <Select
              mode="multiple"
              allowClear
              placeholder="可分配多个角色"
              optionFilterProp="label"
              options={roles.map((r) => ({ label: r.roleName, value: r.roleId }))}
            />
          </Form.Item>

          <Form.Item name="phone" label="手机号">
            <Input placeholder="11 位手机号" maxLength={11} />
          </Form.Item>

          <Form.Item name="email" label="邮箱">
            <Input placeholder="选填" />
          </Form.Item>

          <Form.Item name="sex" label="性别">
            <Select options={SEX_OPTIONS} />
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
            <Input.TextArea rows={3} placeholder="选填" maxLength={200} showCount />
          </Form.Item>
        </Form>
      </Modal>

      {/* ---------- 重置密码弹窗 ---------- */}
      <Modal
        open={pwdOpen}
        title={`重置「${pwdTarget?.username ?? ''}」的密码`}
        onCancel={() => setPwdOpen(false)}
        onOk={submitPwd}
        okText="确定"
        cancelText="取消"
        destroyOnHidden
      >
        <Form form={pwdForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="password"
            label="新密码"
            rules={[
              { required: true, message: '请输入新密码' },
              { min: 6, message: '密码不少于 6 位' },
            ]}
          >
            <Input.Password placeholder="不少于 6 位" autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            name="confirm"
            label="确认密码"
            dependencies={['password']}
            rules={[
              { required: true, message: '请再次输入新密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('password') === value) return Promise.resolve();
                  return Promise.reject(new Error('两次输入的密码不一致'));
                },
              }),
            ]}
          >
            <Input.Password placeholder="再次输入新密码" autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
