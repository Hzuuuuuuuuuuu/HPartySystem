import { useEffect, useState } from 'react';
import { Button, Card, Empty, Modal, Space, Spin, Tag, App as AntdApp, Form, Input, Select, InputNumber } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { listDeptTree, addDept, updateDept, removeDept, type SysDept } from '@/api/system';
import { useUserStore } from '@/store/user';

const ORG_TYPE_LABEL: Record<number, string> = {
  1: '党委',
  2: '党总支',
  3: '党支部',
  4: '党小组',
};

const ORG_TYPE_COLOR: Record<number, string> = {
  1: '#C7000B',
  2: '#FAAD14',
  3: '#1890FF',
  4: '#8C8C8C',
};

/**
 * 党组织架构图（对应图4）。
 *
 * 用纯 CSS 的树形连接线实现，不引入图形库：
 * 结构简单、无版本兼容风险，且样式完全可控。
 */
export default function OrgTreePage() {
  const { message, modal } = AntdApp.useApp();
  const can = useUserStore((s) => s.can);

  const [loading, setLoading] = useState(true);
  const [tree, setTree] = useState<SysDept[]>([]);
  const [selected, setSelected] = useState<SysDept | null>(null);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<SysDept | null>(null);
  const [form] = Form.useForm();
  const [allDepts, setAllDepts] = useState<SysDept[]>([]);

  const fetchTree = async () => {
    setLoading(true);
    try {
      const res = await listDeptTree();
      setTree(res ?? []);
      setAllDepts(flatten(res ?? []));
    } catch {
      setTree([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTree();
  }, []);

  const openAdd = (parentId?: number) => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ parentId: parentId ?? 0, orgType: 3, status: 1 });
    setModalOpen(true);
  };

  const openEdit = (node: SysDept) => {
    setEditing(node);
    form.setFieldsValue(node);
    setModalOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    try {
      if (editing) {
        await updateDept({ ...values, orgId: editing.orgId });
        message.success('修改成功');
      } else {
        await addDept(values);
        message.success('新增成功');
      }
      setModalOpen(false);
      fetchTree();
    } catch {
      // 拦截器已提示
    }
  };

  const confirmRemove = (node: SysDept) => {
    modal.confirm({
      title: `确认删除「${node.orgName}」？`,
      content: '存在下级组织或关联人员时无法删除。',
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await removeDept(node.orgId);
        message.success('删除成功');
        setSelected(null);
        fetchTree();
      },
    });
  };

  return (
    <div>
      <Card variant="borderless" style={{ marginBottom: 12 }} styles={{ body: { padding: 16 } }}>
        <Space wrap>
          {can('system:dept:add') && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => openAdd(selected?.orgId)}>
              新增
            </Button>
          )}
          {can('system:dept:edit') && (
            <Button icon={<EditOutlined />} disabled={!selected} onClick={() => selected && openEdit(selected)}>
              编辑
            </Button>
          )}
          {can('system:dept:remove') && (
            <Button icon={<DeleteOutlined />} disabled={!selected} onClick={() => selected && confirmRemove(selected)}>
              删除
            </Button>
          )}
          <Button icon={<ReloadOutlined />} onClick={fetchTree}>
            刷新
          </Button>
          {selected && (
            <Tag color="red" style={{ marginLeft: 8 }}>
              已选：{selected.orgName}
            </Tag>
          )}
        </Space>
      </Card>

      <Card variant="borderless" styles={{ body: { padding: '20px 20px 40px' } }}>
        <Spin spinning={loading}>
          {tree.length === 0 && !loading ? (
            <Empty description="暂无党组织数据" style={{ padding: '60px 0' }} />
          ) : (
            <div className="org-chart">
              <ul>
                {tree.map((node) => (
                  <OrgNode
                    key={node.orgId}
                    node={node}
                    selectedId={selected?.orgId}
                    onSelect={setSelected}
                    onAddChild={openAdd}
                  />
                ))}
              </ul>
            </div>
          )}
        </Spin>
      </Card>

      <Modal
        open={modalOpen}
        title={editing ? '编辑党组织' : '新增党组织'}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        okText="确定"
        cancelText="取消"
        destroyOnHidden
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="parentId" label="上级组织">
            <Select
              options={[
                { label: '（无，作为根组织）', value: 0 },
                ...allDepts
                  .filter((d) => d.orgId !== editing?.orgId)
                  .map((d) => ({ label: d.orgName, value: d.orgId })),
              ]}
            />
          </Form.Item>
          <Form.Item name="orgName" label="组织名称" rules={[{ required: true, message: '请输入组织名称' }]}>
            <Input placeholder="如：第一支部" />
          </Form.Item>
          <Form.Item name="orgType" label="组织类型" rules={[{ required: true, message: '请选择类型' }]}>
            <Select
              options={Object.entries(ORG_TYPE_LABEL).map(([v, l]) => ({
                label: l,
                value: Number(v),
              }))}
            />
          </Form.Item>
          <Form.Item name="leader" label="负责人">
            <Input />
          </Form.Item>
          <Form.Item name="phone" label="联系电话">
            <Input />
          </Form.Item>
          <Form.Item name="address" label="办公地址">
            <Input />
          </Form.Item>
          <Form.Item name="orderNum" label="显示排序">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

interface OrgNodeProps {
  node: SysDept;
  selectedId?: number;
  onSelect: (node: SysDept) => void;
  onAddChild: (parentId: number) => void;
}

function OrgNode({ node, selectedId, onSelect, onAddChild }: OrgNodeProps) {
  const color = ORG_TYPE_COLOR[node.orgType] ?? '#8C8C8C';
  const active = selectedId === node.orgId;

  return (
    <li>
      <div
        className="org-node"
        onClick={(e) => {
          e.stopPropagation();
          onSelect(node);
        }}
        style={
          active
            ? { borderColor: color, boxShadow: `0 0 0 3px ${color}22` }
            : undefined
        }
        title="单击选中，双击新增下级"
        onDoubleClick={(e) => {
          e.stopPropagation();
          onAddChild(node.orgId);
        }}
      >
        <div className="org-node__photo">{(node.leader ?? node.orgName).slice(0, 1)}</div>
        <div className="org-node__name">{node.leader ?? '—'}</div>
        <div className="org-node__org">{node.orgName}</div>
        <div className="org-node__badge" style={{ background: color }}>
          {ORG_TYPE_LABEL[node.orgType] ?? '组织'}
        </div>
        <div style={{ marginTop: 6, fontSize: 12, color: '#8C8C8C' }}>
          党员 {node.memberCount ?? 0} 名
        </div>
      </div>

      {node.children && node.children.length > 0 && (
        <ul>
          {node.children.map((child) => (
            <OrgNode
              key={child.orgId}
              node={child}
              selectedId={selectedId}
              onSelect={onSelect}
              onAddChild={onAddChild}
            />
          ))}
        </ul>
      )}
    </li>
  );
}

/** 把组织树拍平，供上级组织下拉使用 */
function flatten(nodes: SysDept[], acc: SysDept[] = []): SysDept[] {
  for (const n of nodes) {
    acc.push(n);
    if (n.children?.length) flatten(n.children, acc);
  }
  return acc;
}
