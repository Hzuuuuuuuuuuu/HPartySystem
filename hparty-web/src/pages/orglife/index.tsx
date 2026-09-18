import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  Button,
  Card,
  Empty,
  Form,
  Input,
  Modal,
  Space,
  Spin,
  Table,
  Tag,
  App as AntdApp,
} from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import FuncGrid, { type FuncItem } from '@/components/FuncGrid';
import { http } from '@/api/request';
import TaskNoticeList from '@/pages/meeting/TaskNoticeList';
import { useUserStore } from '@/store/user';

interface MaterialCategory {
  code: string;
  label: string;
  icon: string;
}

/**
 * URL 末段 → 材料分类。
 *
 * 组织生活会下的 11 个菜单共用本页面组件，靠**路径**区分。
 * 早期把筛选值塞在菜单表的 component 字段里，而动态路由按组件文件名解析，
 * 带查询串就找不到文件、路由不注册，11 个菜单全部 404。
 * 这两个后缀与菜单表里的路径段保持一致，不要随意改名。
 */
const SLUG_TO_CATEGORY: Record<string, string> = {
  notice: 'NOTICE',
  'pre-study': 'PRE_STUDY',
  record: 'RECORD',
  analysis: 'ANALYSIS',
  'self-eval': 'SELF_EVAL',
  other: 'OTHER',
  'problem-list': 'PROBLEM_LIST',
  'rectify-list': 'RECTIFY_LIST',
  minutes: 'MEETING_MINUTES',
  'democratic-eval': 'DEMOCRATIC_EVAL',
  report: 'SITUATION_REPORT',
};

const CATEGORY_TO_SLUG: Record<string, string> = Object.fromEntries(
  Object.entries(SLUG_TO_CATEGORY).map(([slug, code]) => [code, slug]),
);

interface Material {
  materialId: number;
  category: string;
  categoryLabel?: string;
  title: string;
  content?: string;
  fileUrl?: string;
  uploadName?: string;
  createTime?: string;
}

export default function OrgLifePage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { message, modal } = AntdApp.useApp();
  const can = useUserStore((s) => s.can);

  // 材料分类由 URL 末段决定，例如 /org-life/problem-list
  const category = SLUG_TO_CATEGORY[location.pathname.split('/').pop() ?? ''] ?? '';

  const [categories, setCategories] = useState<MaterialCategory[]>([]);
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<Material[]>([]);
  const [selected, setSelected] = useState<Material | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm();

  useEffect(() => {
    http
      .get<MaterialCategory[]>('/party/material/categories')
      .then(setCategories)
      .catch(() => setCategories([]));
  }, []);

  const fetchList = async () => {
    setLoading(true);
    try {
      const res = await http.get<Material[]>('/party/material/list', {
        category: category || undefined,
      });
      setRows(res ?? []);
    } catch {
      setRows([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchList();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [category]);

  const entries: FuncItem[] = categories.map((c) => ({
    key: c.code,
    label: c.label,
    icon: c.icon,
    onClick: () => {
      navigationSlug(c.code);
      setSelected(null);
    },
  }));

  /** 切到某个材料分类 —— 走路由跳转，保持 URL 即状态 */
  function navigationSlug(code: string) {
    const slug = CATEGORY_TO_SLUG[code];
    if (slug) {
      navigate(`/org-life/${slug}`);
    }
  }

  const submit = async () => {
    const values = await form.validateFields();
    try {
      await http.post('/party/material', {
        ...values,
        materialId: selected?.materialId,
        category: category || 'OTHER',
      });
      message.success(selected ? '修改成功' : '新增成功');
      setModalOpen(false);
      form.resetFields();
      setSelected(null);
      fetchList();
    } catch {
      // 拦截器已提示
    }
  };

  const confirmRemove = () => {
    if (!selected) return;
    modal.confirm({
      title: `确认删除「${selected.title}」？`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await http.delete(`/party/material/${selected.materialId}`);
        message.success('删除成功');
        setSelected(null);
        fetchList();
      },
    });
  };

  const currentLabel = categories.find((c) => c.code === category)?.label ?? '全部材料';

  return (
    <div>
      <div style={{ marginBottom: 12 }}>
        <FuncGrid items={entries} />
      </div>

      <Card
        variant="borderless"
        title={currentLabel}
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={fetchList}>
              刷新
            </Button>
            {can('orglife:edit') && (
              <Button
                icon={<EditOutlined />}
                disabled={!selected}
                onClick={() => {
                  if (!selected) return;
                  form.setFieldsValue(selected);
                  setModalOpen(true);
                }}
              >
                编辑
              </Button>
            )}
            {can('orglife:remove') && (
              <Button icon={<DeleteOutlined />} disabled={!selected} onClick={confirmRemove}>
                删除
              </Button>
            )}
            {can('orglife:add') && (
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => {
                  form.resetFields();
                  setSelected(null);
                  setModalOpen(true);
                }}
              >
                新增材料
              </Button>
            )}
          </Space>
        }
        style={{ marginBottom: 12 }}
      >
        <Spin spinning={loading}>
          {rows.length === 0 && !loading ? (
            <Empty description="暂无材料" style={{ padding: '40px 0' }} />
          ) : (
            <Table
              rowKey="materialId"
              dataSource={rows}
              size="middle"
              pagination={{ pageSize: 10, showSizeChanger: false }}
              onRow={(record) => ({
                onClick: () => setSelected(record),
                style: {
                  cursor: 'pointer',
                  background:
                    selected?.materialId === record.materialId ? '#FFF5F5' : undefined,
                },
              })}
              columns={[
                {
                  title: '分类',
                  dataIndex: 'categoryLabel',
                  width: 130,
                  render: (v: string) => <Tag color="red">{v}</Tag>,
                },
                { title: '标题', dataIndex: 'title', ellipsis: true },
                {
                  title: '内容摘要',
                  dataIndex: 'content',
                  ellipsis: true,
                  render: (v: string) => v || '—',
                },
                { title: '上传人', dataIndex: 'uploadName', width: 110 },
                {
                  title: '上传时间',
                  dataIndex: 'createTime',
                  width: 170,
                  render: (v: string) => v || '—',
                },
              ]}
            />
          )}
        </Spin>
      </Card>

      <TaskNoticeList />

      <Modal
        open={modalOpen}
        title={selected ? '编辑材料' : `新增${currentLabel}`}
        onCancel={() => {
          setModalOpen(false);
          setSelected(null);
        }}
        onOk={submit}
        okText="确定"
        cancelText="取消"
        width={640}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input placeholder="如：关于召开2026年度组织生活会的通知" />
          </Form.Item>
          <Form.Item name="content" label="内容">
            <Input.TextArea rows={6} placeholder="填写材料正文" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
