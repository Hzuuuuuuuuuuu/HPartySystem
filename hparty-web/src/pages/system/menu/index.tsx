import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ComponentType, CSSProperties, Key } from 'react';
import {
  App as AntdApp,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Radio,
  Select,
  Space,
  Table,
  Tag,
  TreeSelect,
} from 'antd';
import {
  ApartmentOutlined,
  AppstoreOutlined,
  AuditOutlined,
  BankOutlined,
  BarChartOutlined,
  BellOutlined,
  BookOutlined,
  CalendarOutlined,
  ClusterOutlined,
  DashboardOutlined,
  DeleteOutlined,
  EditOutlined,
  FileDoneOutlined,
  FileSearchOutlined,
  FileTextOutlined,
  FileWordOutlined,
  FormOutlined,
  IdcardOutlined,
  KeyOutlined,
  LineChartOutlined,
  LoginOutlined,
  MenuOutlined,
  NotificationOutlined,
  PartitionOutlined,
  PieChartOutlined,
  PlusOutlined,
  ProfileOutlined,
  ProjectOutlined,
  QuestionCircleOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  SettingOutlined,
  SolutionOutlined,
  StarOutlined,
  TeamOutlined,
  TrophyOutlined,
  UserOutlined,
  UsergroupAddOutlined,
  WalletOutlined,
} from '@ant-design/icons';
import {
  addMenu,
  listMenus,
  removeMenu,
  updateMenu,
  type SysMenu,
} from '@/api/system';
import { useUserStore } from '@/store/user';

/** 常用图标，icon 字段存的是 Ant Design 图标组件名 */
const ICON_MAP: Record<string, ComponentType<{ style?: CSSProperties; className?: string }>> = {
  ApartmentOutlined,
  AppstoreOutlined,
  AuditOutlined,
  BankOutlined,
  BarChartOutlined,
  BellOutlined,
  BookOutlined,
  CalendarOutlined,
  ClusterOutlined,
  DashboardOutlined,
  FileDoneOutlined,
  FileSearchOutlined,
  FileTextOutlined,
  FileWordOutlined,
  FormOutlined,
  IdcardOutlined,
  KeyOutlined,
  LineChartOutlined,
  LoginOutlined,
  MenuOutlined,
  NotificationOutlined,
  PartitionOutlined,
  PieChartOutlined,
  ProfileOutlined,
  ProjectOutlined,
  QuestionCircleOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  SolutionOutlined,
  StarOutlined,
  TeamOutlined,
  TrophyOutlined,
  UserOutlined,
  UsergroupAddOutlined,
  WalletOutlined,
};

const MENU_TYPE_LABEL: Record<string, string> = { M: '目录', C: '菜单', F: '按钮' };
const MENU_TYPE_COLOR: Record<string, string> = { M: 'blue', C: 'green', F: 'default' };

interface TreeNode {
  value: number;
  title: string;
  children?: TreeNode[];
}

/** 平铺菜单 → 树（按 orderNum 排序） */
function buildTree(list: SysMenu[], parentId = 0): SysMenu[] {
  return list
    .filter((m) => (m.parentId ?? 0) === parentId)
    .sort((a, b) => (a.orderNum ?? 0) - (b.orderNum ?? 0))
    .map((m) => {
      const children = buildTree(list, m.menuId);
      return children.length ? { ...m, children } : { ...m };
    });
}

/** 关键字 + 状态过滤，命中节点的祖先一并保留以维持层级 */
function filterMenus(list: SysMenu[], keyword: string, status?: number): SysMenu[] {
  const kw = keyword.trim().toLowerCase();
  if (!kw && status === undefined) return list;

  const byId = new Map<number, SysMenu>(list.map((m) => [m.menuId, m]));
  const keep = new Set<number>();

  for (const m of list) {
    const nameHit = !kw || m.menuName.toLowerCase().includes(kw);
    const statusHit = status === undefined || m.status === status;
    if (!nameHit || !statusHit) continue;

    keep.add(m.menuId);
    let parent = m.parentId ?? 0;
    while (parent && byId.has(parent)) {
      keep.add(parent);
      parent = byId.get(parent)?.parentId ?? 0;
    }
  }
  return list.filter((m) => keep.has(m.menuId));
}

/** 上级菜单下拉数据，编辑时排除自身及其子树 */
function toParentTree(nodes: SysMenu[], excludeId?: number): TreeNode[] {
  return nodes
    .filter((n) => n.menuId !== excludeId)
    .map((n) => ({
      value: n.menuId,
      title: n.menuName,
      children: n.children?.length ? toParentTree(n.children, excludeId) : undefined,
    }));
}

/**
 * 菜单管理。
 *
 * 表格以树形展示全部层级（目录 / 菜单 / 按钮），支持展开收起与增删改。
 */
export default function SystemMenuPage() {
  const { message, modal } = AntdApp.useApp();
  const can = useUserStore((s) => s.can);

  const [loading, setLoading] = useState(false);
  const [flatMenus, setFlatMenus] = useState<SysMenu[]>([]);
  const [selected, setSelected] = useState<SysMenu | null>(null);
  const [expandedKeys, setExpandedKeys] = useState<readonly Key[]>([]);

  const [searchForm] = Form.useForm();
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<number | undefined>(undefined);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<SysMenu | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();
  const menuType = Form.useWatch('menuType', form) as string | undefined;

  /** 拉取全部菜单并拍平成树 */
  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      const list = await listMenus();
      setFlatMenus(list);
      setExpandedKeys(list.map((m) => m.menuId));
    } catch {
      setFlatMenus([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchList();
  }, [fetchList]);

  /** 展示用树：先过滤，再按 parentId 组装 */
  const treeData = useMemo(
    () => buildTree(filterMenus(flatMenus, keyword, statusFilter)),
    [flatMenus, keyword, statusFilter],
  );

  /** 完整树，供上级菜单下拉使用 */
  const fullTree = useMemo(() => buildTree(flatMenus), [flatMenus]);

  const iconOptions = useMemo(
    () =>
      Object.keys(ICON_MAP).map((name) => {
        const Icon = ICON_MAP[name];
        return { label: <Space size={6}><Icon />{name}</Space>, value: name };
      }),
    [],
  );

  /** 打开新增弹窗，parentId 可指定上级 */
  const openAdd = (parentId = 0) => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ parentId, menuType: 'C', orderNum: 0, visible: 1, status: 1, isFrame: 0, isCache: 0 });
    setModalOpen(true);
  };

  const openEdit = (row: SysMenu) => {
    setEditing(row);
    form.resetFields();
    form.setFieldsValue(row);
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      if (editing) {
        await updateMenu({ ...values, menuId: editing.menuId });
        message.success('修改成功');
      } else {
        await addMenu(values);
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

  const confirmRemove = (row: SysMenu) => {
    modal.confirm({
      title: `确认删除菜单「${row.menuName}」？`,
      content: '存在子菜单时无法删除，请先删除下级节点。',
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await removeMenu(row.menuId);
        message.success('删除成功');
        setSelected(null);
        fetchList();
      },
    });
  };

  /** 图标单元格 */
  const renderIcon = (name?: string) => {
    const Icon = name ? ICON_MAP[name] : undefined;
    return Icon ? <Icon style={{ fontSize: 16 }} /> : <span style={{ color: '#BFBFBF' }}>—</span>;
  };

  return (
    <div>
      {/* ---------- 工具栏 ---------- */}
      <Card variant="borderless" style={{ marginBottom: 12 }} styles={{ body: { padding: 16 } }}>
        <Form form={searchForm} layout="inline" style={{ marginBottom: 12, rowGap: 12 }}>
          <Form.Item name="menuName" label="菜单名称">
            <Input
              allowClear
              placeholder="菜单名称"
              style={{ width: 160 }}
              onPressEnter={(e) => setKeyword((e.target as HTMLInputElement).value)}
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
              <Button
                type="primary"
                icon={<SearchOutlined />}
                onClick={() => {
                  const v = searchForm.getFieldsValue();
                  setKeyword(v.menuName ?? '');
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
          {can('system:menu:add') && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => openAdd(0)}>
              新增
            </Button>
          )}
          {can('system:menu:edit') && (
            <Button icon={<EditOutlined />} disabled={!selected} onClick={() => selected && openEdit(selected)}>
              编辑
            </Button>
          )}
          {can('system:menu:remove') && (
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
          {selected && <Tag color="red">已选：{selected.menuName}</Tag>}
        </Space>
      </Card>

      {/* ---------- 树形表格 ---------- */}
      <Card variant="borderless" styles={{ body: { padding: 16 } }}>
        <Table<SysMenu>
          rowKey="menuId"
          loading={loading}
          dataSource={treeData}
          size="middle"
          pagination={false}
          scroll={{ x: 1100 }}
          expandable={{
            expandedRowKeys: expandedKeys,
            onExpandedRowsChange: setExpandedKeys,
          }}
          onRow={(record) => ({
            onClick: () => setSelected(record),
            style: {
              cursor: 'pointer',
              background: selected?.menuId === record.menuId ? '#FFF5F5' : undefined,
            },
          })}
          columns={[
            { title: '菜单名称', dataIndex: 'menuName', width: 220 },
            {
              title: '图标',
              dataIndex: 'icon',
              width: 80,
              align: 'center',
              render: (v: string) => renderIcon(v),
            },
            { title: '排序', dataIndex: 'orderNum', width: 80, render: (v: number) => v ?? 0 },
            {
              title: '类型',
              dataIndex: 'menuType',
              width: 90,
              render: (v: string) => (
                <Tag color={MENU_TYPE_COLOR[v] ?? 'default'}>{MENU_TYPE_LABEL[v] ?? v}</Tag>
              ),
            },
            {
              title: '权限标识',
              dataIndex: 'perms',
              width: 220,
              render: (v: string) => v || '—',
            },
            { title: '路由地址', dataIndex: 'path', width: 180, render: (v: string) => v || '—' },
            { title: '组件路径', dataIndex: 'component', ellipsis: true, render: (v: string) => v || '—' },
            {
              title: '状态',
              dataIndex: 'status',
              width: 90,
              render: (v: number) => (
                <Tag color={v === 1 ? 'success' : 'default'}>{v === 1 ? '正常' : '停用'}</Tag>
              ),
            },
            {
              title: '显示',
              dataIndex: 'visible',
              width: 90,
              render: (v: number) => (
                <Tag color={v === 0 ? 'default' : 'blue'}>{v === 0 ? '隐藏' : '显示'}</Tag>
              ),
            },
            {
              title: '操作',
              key: 'action',
              width: 200,
              fixed: 'right',
              render: (_: unknown, row) => (
                <Space size={4}>
                  {can('system:menu:add') && row.menuType !== 'F' && (
                    <Button type="link" size="small" onClick={() => openAdd(row.menuId)}>
                      新增下级
                    </Button>
                  )}
                  {can('system:menu:edit') && (
                    <Button type="link" size="small" onClick={() => openEdit(row)}>
                      编辑
                    </Button>
                  )}
                  {can('system:menu:remove') && (
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
        title={editing ? '编辑菜单' : '新增菜单'}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        confirmLoading={submitting}
        okText="确定"
        cancelText="取消"
        width={640}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="parentId" label="上级菜单">
            <TreeSelect
              showSearch
              treeNodeFilterProp="title"
              placeholder="不选则作为顶级目录"
              treeData={toParentTree(fullTree, editing?.menuId)}
            />
          </Form.Item>

          <Form.Item name="menuType" label="菜单类型" rules={[{ required: true, message: '请选择菜单类型' }]}>
            <Radio.Group
              optionType="button"
              buttonStyle="solid"
              options={[
                { label: '目录', value: 'M' },
                { label: '菜单', value: 'C' },
                { label: '按钮', value: 'F' },
              ]}
            />
          </Form.Item>

          <Form.Item name="menuName" label="菜单名称" rules={[{ required: true, message: '请输入菜单名称' }]}>
            <Input placeholder="如：用户管理" />
          </Form.Item>

          <Form.Item name="orderNum" label="显示排序">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>

          {menuType !== 'F' && (
            <>
              <Form.Item name="icon" label="菜单图标">
                <Select
                  allowClear
                  showSearch
                  optionFilterProp="value"
                  placeholder="选择图标"
                  options={iconOptions}
                />
              </Form.Item>

              <Form.Item name="path" label="路由地址">
                <Input placeholder="如：user 或 https://example.com" />
              </Form.Item>
            </>
          )}

          {menuType === 'C' && (
            <>
              <Form.Item name="component" label="组件路径">
                <Input placeholder="如：system/user/index（对应 src/pages 下的相对路径）" />
              </Form.Item>
              <Form.Item name="query" label="路由参数">
                <Input placeholder="如：type=MEMBER_ASSEMBLY" />
              </Form.Item>
              <Form.Item name="isFrame" label="是否外链">
                <Radio.Group
                  options={[
                    { label: '是', value: 1 },
                    { label: '否', value: 0 },
                  ]}
                />
              </Form.Item>
              <Form.Item name="isCache" label="是否缓存">
                <Radio.Group
                  options={[
                    { label: '缓存', value: 1 },
                    { label: '不缓存', value: 0 },
                  ]}
                />
              </Form.Item>
            </>
          )}

          {menuType !== 'M' && (
            <Form.Item name="perms" label="权限标识">
              <Input placeholder="如：system:user:list" />
            </Form.Item>
          )}

          <Form.Item name="visible" label="显示状态">
            <Radio.Group
              options={[
                { label: '显示', value: 1 },
                { label: '隐藏', value: 0 },
              ]}
            />
          </Form.Item>

          <Form.Item name="status" label="菜单状态">
            <Radio.Group
              options={[
                { label: '正常', value: 1 },
                { label: '停用', value: 0 },
              ]}
            />
          </Form.Item>

          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} placeholder="选填" maxLength={200} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
