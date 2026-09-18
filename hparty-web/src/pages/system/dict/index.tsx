import { useCallback, useEffect, useState } from 'react';
import {
  App as AntdApp,
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Radio,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  SyncOutlined,
} from '@ant-design/icons';
import {
  addDictData,
  addDictType,
  pageDictData,
  pageDictTypes,
  refreshDictCache,
  removeDictData,
  removeDictType,
  updateDictData,
  updateDictType,
  type DictData,
  type DictType,
  type DictTypeQuery,
} from '@/api/system';
import { useUserStore } from '@/store/user';

const PAGE_SIZE = 10;

/** 回显样式 → 标签颜色 */
const LIST_CLASS_COLOR: Record<string, string> = {
  default: 'default',
  primary: 'blue',
  success: 'green',
  info: 'cyan',
  warning: 'orange',
  danger: 'red',
};

const LIST_CLASS_OPTIONS = Object.keys(LIST_CLASS_COLOR).map((v) => ({ label: v, value: v }));

/**
 * 字典管理。
 *
 * 左右分栏：左侧字典类型，选中后右侧维护该类型下的字典数据。
 */
export default function SystemDictPage() {
  const { message, modal } = AntdApp.useApp();
  const can = useUserStore((s) => s.can);

  // ---------- 左侧：字典类型 ----------
  const [typeLoading, setTypeLoading] = useState(false);
  const [types, setTypes] = useState<DictType[]>([]);
  const [typeTotal, setTypeTotal] = useState(0);
  const [typePageNum, setTypePageNum] = useState(1);
  const [typeQuery, setTypeQuery] = useState<DictTypeQuery>({});
  const [selectedType, setSelectedType] = useState<DictType | null>(null);

  const [typeForm] = Form.useForm();
  const [typeSearchForm] = Form.useForm();
  const [typeModalOpen, setTypeModalOpen] = useState(false);
  const [editingType, setEditingType] = useState<DictType | null>(null);

  // ---------- 右侧：字典数据 ----------
  const [dataLoading, setDataLoading] = useState(false);
  const [dataRows, setDataRows] = useState<DictData[]>([]);
  const [dataTotal, setDataTotal] = useState(0);
  const [dataPageNum, setDataPageNum] = useState(1);
  const [selectedData, setSelectedData] = useState<DictData | null>(null);

  const [dataForm] = Form.useForm();
  const [dataModalOpen, setDataModalOpen] = useState(false);
  const [editingData, setEditingData] = useState<DictData | null>(null);
  const [submitting, setSubmitting] = useState(false);

  /** 拉取字典类型分页 */
  const fetchTypes = useCallback(async () => {
    setTypeLoading(true);
    try {
      const res = await pageDictTypes({ ...typeQuery, pageNum: typePageNum, pageSize: PAGE_SIZE });
      const records = res.records ?? [];
      setTypes(records);
      setTypeTotal(res.total ?? 0);
      // 默认选中第一条，保证右侧不空
      setSelectedType((prev) => {
        if (prev && records.some((t) => t.dictId === prev.dictId)) return prev;
        return records[0] ?? null;
      });
    } catch {
      setTypes([]);
      setTypeTotal(0);
      setSelectedType(null);
    } finally {
      setTypeLoading(false);
    }
  }, [typeQuery, typePageNum]);

  /** 拉取当前类型的字典数据 */
  const fetchData = useCallback(async () => {
    if (!selectedType) {
      setDataRows([]);
      setDataTotal(0);
      return;
    }
    setDataLoading(true);
    try {
      const res = await pageDictData({
        dictType: selectedType.dictType,
        pageNum: dataPageNum,
        pageSize: PAGE_SIZE,
      });
      setDataRows(res.records ?? []);
      setDataTotal(res.total ?? 0);
    } catch {
      setDataRows([]);
      setDataTotal(0);
    } finally {
      setDataLoading(false);
    }
  }, [selectedType, dataPageNum]);

  useEffect(() => {
    fetchTypes();
  }, [fetchTypes]);

  useEffect(() => {
    setDataPageNum(1);
    setSelectedData(null);
  }, [selectedType?.dictId]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  // ---------- 字典类型操作 ----------

  const openAddType = () => {
    setEditingType(null);
    typeForm.resetFields();
    typeForm.setFieldsValue({ status: 1 });
    setTypeModalOpen(true);
  };

  const openEditType = (row: DictType) => {
    setEditingType(row);
    typeForm.resetFields();
    typeForm.setFieldsValue(row);
    setTypeModalOpen(true);
  };

  const submitType = async () => {
    const values = await typeForm.validateFields();
    try {
      if (editingType) {
        await updateDictType({ ...values, dictId: editingType.dictId });
        message.success('修改成功');
      } else {
        await addDictType(values);
        message.success('新增成功');
      }
      setTypeModalOpen(false);
      fetchTypes();
    } catch {
      // 拦截器已提示
    }
  };

  const confirmRemoveType = (row: DictType) => {
    modal.confirm({
      title: `确认删除字典类型「${row.dictName}」？`,
      content: '该类型下的字典数据将一并失效，请谨慎操作。',
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await removeDictType(row.dictId);
        message.success('删除成功');
        setSelectedType(null);
        fetchTypes();
      },
    });
  };

  /** 刷新字典缓存 */
  const doRefreshCache = () => {
    modal.confirm({
      title: '确认刷新字典缓存？',
      content: '刷新后所有字典将重新从数据库加载。',
      okText: '确认刷新',
      cancelText: '取消',
      onOk: async () => {
        await refreshDictCache();
        message.success('缓存已刷新');
      },
    });
  };

  // ---------- 字典数据操作 ----------

  const openAddData = () => {
    if (!selectedType) {
      message.warning('请先在左侧选择字典类型');
      return;
    }
    setEditingData(null);
    dataForm.resetFields();
    dataForm.setFieldsValue({ dictType: selectedType.dictType, dictSort: 0, status: 1, listClass: 'default' });
    setDataModalOpen(true);
  };

  const openEditData = (row: DictData) => {
    setEditingData(row);
    dataForm.resetFields();
    dataForm.setFieldsValue(row);
    setDataModalOpen(true);
  };

  const submitData = async () => {
    const values = await dataForm.validateFields();
    setSubmitting(true);
    try {
      if (editingData) {
        await updateDictData({ ...values, dictCode: editingData.dictCode });
        message.success('修改成功');
      } else {
        await addDictData(values);
        message.success('新增成功');
      }
      setDataModalOpen(false);
      fetchData();
    } catch {
      // 拦截器已提示
    } finally {
      setSubmitting(false);
    }
  };

  const confirmRemoveData = (row: DictData) => {
    modal.confirm({
      title: `确认删除字典项「${row.dictLabel}」？`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await removeDictData(row.dictCode);
        message.success('删除成功');
        setSelectedData(null);
        fetchData();
      },
    });
  };

  return (
    <Row gutter={12}>
      {/* ---------- 左侧：字典类型 ---------- */}
      <Col xs={24} lg={9} xl={8}>
        <Card
          variant="borderless"
          title="字典类型"
          styles={{ body: { padding: 16 } }}
          extra={
            <Button size="small" icon={<SyncOutlined />} onClick={doRefreshCache}>
              刷新缓存
            </Button>
          }
        >
          <Form
            form={typeSearchForm}
            layout="inline"
            style={{ marginBottom: 12, rowGap: 8 }}
            onFinish={(v) => {
              setTypeQuery({
                dictName: v.dictName || undefined,
                dictType: v.dictType || undefined,
                status: v.status ?? undefined,
              });
              setTypePageNum(1);
            }}
          >
            <Form.Item name="dictName" style={{ marginInlineEnd: 8 }}>
              <Input allowClear placeholder="字典名称" style={{ width: 120 }} />
            </Form.Item>
            <Form.Item name="dictType" style={{ marginInlineEnd: 8 }}>
              <Input allowClear placeholder="字典类型" style={{ width: 130 }} />
            </Form.Item>
            <Form.Item style={{ marginInlineEnd: 0 }}>
              <Button type="primary" size="small" htmlType="submit" icon={<SearchOutlined />}>
                查询
              </Button>
            </Form.Item>
          </Form>

          <Space wrap style={{ marginBottom: 12 }}>
            {can('system:dict:add') && (
              <Button type="primary" size="small" icon={<PlusOutlined />} onClick={openAddType}>
                新增
              </Button>
            )}
            {can('system:dict:edit') && (
              <Button
                size="small"
                icon={<EditOutlined />}
                disabled={!selectedType}
                onClick={() => selectedType && openEditType(selectedType)}
              >
                编辑
              </Button>
            )}
            {can('system:dict:remove') && (
              <Button
                size="small"
                icon={<DeleteOutlined />}
                disabled={!selectedType}
                onClick={() => selectedType && confirmRemoveType(selectedType)}
              >
                删除
              </Button>
            )}
            <Button size="small" icon={<ReloadOutlined />} onClick={fetchTypes}>
              刷新
            </Button>
          </Space>

          <Table<DictType>
            rowKey="dictId"
            size="small"
            loading={typeLoading}
            dataSource={types}
            pagination={{
              current: typePageNum,
              pageSize: PAGE_SIZE,
              total: typeTotal,
              showSizeChanger: false,
              size: 'small',
              onChange: (p) => setTypePageNum(p),
            }}
            onRow={(record) => ({
              onClick: () => setSelectedType(record),
              style: {
                cursor: 'pointer',
                background: selectedType?.dictId === record.dictId ? '#FFF5F5' : undefined,
              },
            })}
            columns={[
              { title: '字典名称', dataIndex: 'dictName', ellipsis: true },
              { title: '字典类型', dataIndex: 'dictType', ellipsis: true },
              {
                title: '状态',
                dataIndex: 'status',
                width: 80,
                render: (v: number) => (
                  <Tag color={v === 0 ? 'default' : 'success'}>{v === 0 ? '停用' : '正常'}</Tag>
                ),
              },
            ]}
          />
        </Card>
      </Col>

      {/* ---------- 右侧：字典数据 ---------- */}
      <Col xs={24} lg={15} xl={16}>
        <Card
          variant="borderless"
          title={
            selectedType ? (
              <Space size={8}>
                <span>字典数据</span>
                <Tag color="red">{selectedType.dictName}</Tag>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {selectedType.dictType}
                </Typography.Text>
              </Space>
            ) : (
              '字典数据'
            )
          }
          styles={{ body: { padding: 16 } }}
          extra={
            <Space>
              {can('system:dict:add') && (
                <Button type="primary" icon={<PlusOutlined />} disabled={!selectedType} onClick={openAddData}>
                  新增
                </Button>
              )}
              {can('system:dict:edit') && (
                <Button
                  icon={<EditOutlined />}
                  disabled={!selectedData}
                  onClick={() => selectedData && openEditData(selectedData)}
                >
                  编辑
                </Button>
              )}
              {can('system:dict:remove') && (
                <Button
                  icon={<DeleteOutlined />}
                  disabled={!selectedData}
                  onClick={() => selectedData && confirmRemoveData(selectedData)}
                >
                  删除
                </Button>
              )}
              <Button icon={<ReloadOutlined />} onClick={fetchData}>
                刷新
              </Button>
            </Space>
          }
        >
          {!selectedType ? (
            <Empty description="请先在左侧选择字典类型" style={{ padding: '60px 0' }} />
          ) : (
            <Table<DictData>
              rowKey="dictCode"
              size="middle"
              loading={dataLoading}
              dataSource={dataRows}
              pagination={{
                current: dataPageNum,
                pageSize: PAGE_SIZE,
                total: dataTotal,
                showSizeChanger: false,
                showTotal: (t) => `共 ${t} 条`,
                onChange: (p) => {
                  setDataPageNum(p);
                  setSelectedData(null);
                },
              }}
              onRow={(record) => ({
                onClick: () => setSelectedData(record),
                style: {
                  cursor: 'pointer',
                  background: selectedData?.dictCode === record.dictCode ? '#FFF5F5' : undefined,
                },
              })}
              columns={[
                {
                  title: '字典标签',
                  dataIndex: 'dictLabel',
                  render: (v: string, row) => (
                    <Tag color={LIST_CLASS_COLOR[row.listClass ?? 'default'] ?? 'default'}>{v}</Tag>
                  ),
                },
                { title: '字典键值', dataIndex: 'dictValue', width: 140 },
                { title: '排序', dataIndex: 'dictSort', width: 80, render: (v: number) => v ?? 0 },
                {
                  title: '默认',
                  dataIndex: 'isDefault',
                  width: 80,
                  render: (v: number) => (v === 1 ? <Tag color="blue">是</Tag> : '否'),
                },
                {
                  title: '状态',
                  dataIndex: 'status',
                  width: 90,
                  render: (v: number) => (
                    <Tag color={v === 0 ? 'default' : 'success'}>{v === 0 ? '停用' : '正常'}</Tag>
                  ),
                },
                { title: '备注', dataIndex: 'remark', ellipsis: true, render: (v: string) => v || '—' },
              ]}
            />
          )}
        </Card>
      </Col>

      {/* ---------- 字典类型弹窗 ---------- */}
      <Modal
        open={typeModalOpen}
        title={editingType ? '编辑字典类型' : '新增字典类型'}
        onCancel={() => setTypeModalOpen(false)}
        onOk={submitType}
        okText="确定"
        cancelText="取消"
        destroyOnHidden
      >
        <Form form={typeForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="dictName" label="字典名称" rules={[{ required: true, message: '请输入字典名称' }]}>
            <Input placeholder="如：用户性别" />
          </Form.Item>
          <Form.Item
            name="dictType"
            label="字典类型"
            rules={[{ required: true, message: '请输入字典类型' }]}
          >
            <Input placeholder="如：sys_user_sex" disabled={!!editingType} />
          </Form.Item>
          <Form.Item name="status" label="状态">
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

      {/* ---------- 字典数据弹窗 ---------- */}
      <Modal
        open={dataModalOpen}
        title={editingData ? '编辑字典数据' : '新增字典数据'}
        onCancel={() => setDataModalOpen(false)}
        onOk={submitData}
        confirmLoading={submitting}
        okText="确定"
        cancelText="取消"
        destroyOnHidden
      >
        <Form form={dataForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="dictType" label="字典类型">
            <Input disabled />
          </Form.Item>
          <Form.Item name="dictLabel" label="字典标签" rules={[{ required: true, message: '请输入字典标签' }]}>
            <Input placeholder="如：男" />
          </Form.Item>
          <Form.Item name="dictValue" label="字典键值" rules={[{ required: true, message: '请输入字典键值' }]}>
            <Input placeholder="如：1" />
          </Form.Item>
          <Form.Item name="dictSort" label="显示排序">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="listClass" label="回显样式">
            <Select options={LIST_CLASS_OPTIONS} />
          </Form.Item>
          <Form.Item name="isDefault" label="是否默认">
            <Radio.Group
              options={[
                { label: '是', value: 1 },
                { label: '否', value: 0 },
              ]}
            />
          </Form.Item>
          <Form.Item name="status" label="状态">
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
    </Row>
  );
}
