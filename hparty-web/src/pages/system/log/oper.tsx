import { useCallback, useEffect, useState } from 'react';
import type { Key } from 'react';
import {
  App as AntdApp,
  Alert,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Drawer,
  Form,
  Input,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from 'antd';
import {
  ClearOutlined,
  DeleteOutlined,
  ProfileOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import type { Dayjs } from 'dayjs';
import {
  cleanOperLogs,
  getOperLog,
  pageOperLogs,
  removeOperLogs,
  type OperLog,
  type OperLogQuery,
} from '@/api/system';
import { useUserStore } from '@/store/user';

const PAGE_SIZE = 10;
const { RangePicker } = DatePicker;
const { Paragraph, Text } = Typography;

/** 业务类型：0=其它 1=新增 2=修改 3=删除 4=审批 5=导出 6=上传 7=导入 8=授权 */
const BUSINESS_TYPE: Record<number, { label: string; color: string }> = {
  0: { label: '其它', color: 'default' },
  1: { label: '新增', color: 'green' },
  2: { label: '修改', color: 'blue' },
  3: { label: '删除', color: 'red' },
  4: { label: '审批', color: 'purple' },
  5: { label: '导出', color: 'cyan' },
  6: { label: '上传', color: 'orange' },
  7: { label: '导入', color: 'geekblue' },
  8: { label: '授权', color: 'magenta' },
};

const BUSINESS_TYPE_OPTIONS = Object.entries(BUSINESS_TYPE).map(([v, item]) => ({
  label: item.label,
  value: Number(v),
}));

/**
 * 后端 beginTime / endTime 是 LocalDateTime，
 * Spring Boot 默认转换器只认 ISO 格式（yyyy-MM-ddTHH:mm:ss），
 * 空格分隔的写法会直接 400，这里统一按天展开成 ISO 串。
 */
function toIso(date: Dayjs, endOfDay = false) {
  return `${date.format('YYYY-MM-DD')}T${endOfDay ? '23:59:59' : '00:00:00'}`;
}

/** JSON 字符串美化，非 JSON 原样返回 */
function prettyJson(text?: string) {
  if (!text) return '';
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
}

/** 操作日志 */
export default function OperLogPage() {
  const { message, modal } = AntdApp.useApp();
  const can = useUserStore((s) => s.can);

  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<OperLog[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNum, setPageNum] = useState(1);
  const [selectedRowKeys, setSelectedRowKeys] = useState<Key[]>([]);

  const [searchForm] = Form.useForm();
  const [query, setQuery] = useState<OperLogQuery>({});

  // 详情抽屉
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<OperLog | null>(null);

  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      const res = await pageOperLogs({ ...query, pageNum, pageSize: PAGE_SIZE });
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

  const doSearch = () => {
    const values = searchForm.getFieldsValue();
    const range = values.range as [Dayjs, Dayjs] | undefined;
    setQuery({
      title: values.title || undefined,
      username: values.username || undefined,
      businessType: values.businessType ?? undefined,
      status: values.status ?? undefined,
      beginTime: range?.[0] ? toIso(range[0]) : undefined,
      endTime: range?.[1] ? toIso(range[1], true) : undefined,
    });
    setPageNum(1);
    setSelectedRowKeys([]);
  };

  const doReset = () => {
    searchForm.resetFields();
    setQuery({});
    setPageNum(1);
    setSelectedRowKeys([]);
  };

  /** 打开详情抽屉，拉取完整日志 */
  const openDetail = async (row: OperLog) => {
    setDrawerOpen(true);
    setDetail(row);
    setDetailLoading(true);
    try {
      setDetail(await getOperLog(row.operId));
    } catch {
      // 拉取失败时保留列表行数据，拦截器已提示
    } finally {
      setDetailLoading(false);
    }
  };

  const confirmRemove = () => {
    if (!selectedRowKeys.length) return;
    modal.confirm({
      title: `确认删除选中的 ${selectedRowKeys.length} 条操作日志？`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await removeOperLogs(selectedRowKeys.join(','));
        message.success('删除成功');
        setSelectedRowKeys([]);
        fetchList();
      },
    });
  };

  const confirmClean = () => {
    modal.confirm({
      title: '确认清空全部操作日志？',
      content: '清空后数据不可恢复，请谨慎操作。',
      okText: '确认清空',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await cleanOperLogs();
        message.success('已清空');
        setSelectedRowKeys([]);
        fetchList();
      },
    });
  };

  return (
    <div>
      {/* ---------- 工具栏 ---------- */}
      <Card variant="borderless" style={{ marginBottom: 12 }} styles={{ body: { padding: 16 } }}>
        <Form form={searchForm} layout="inline" style={{ marginBottom: 12, rowGap: 12 }}>
          <Form.Item name="title" label="操作模块">
            <Input allowClear placeholder="系统模块" style={{ width: 150 }} onPressEnter={doSearch} />
          </Form.Item>
          <Form.Item name="username" label="操作人员">
            <Input allowClear placeholder="操作人" style={{ width: 130 }} onPressEnter={doSearch} />
          </Form.Item>
          <Form.Item name="businessType" label="操作类型">
            <Select
              allowClear
              placeholder="全部"
              style={{ width: 120 }}
              options={BUSINESS_TYPE_OPTIONS}
            />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select
              allowClear
              placeholder="全部"
              style={{ width: 110 }}
              options={[
                { label: '成功', value: 1 },
                { label: '失败', value: 0 },
              ]}
            />
          </Form.Item>
          <Form.Item name="range" label="操作时间">
            <RangePicker style={{ width: 250 }} />
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
          {can('system:operlog:remove') && (
            <Button icon={<DeleteOutlined />} disabled={!selectedRowKeys.length} onClick={confirmRemove}>
              删除
            </Button>
          )}
          {can('system:operlog:clean') && (
            <Button icon={<ClearOutlined />} danger onClick={confirmClean}>
              清空
            </Button>
          )}
          <Button icon={<ReloadOutlined />} onClick={fetchList}>
            刷新
          </Button>
          {selectedRowKeys.length > 0 && <Tag color="red">已选 {selectedRowKeys.length} 条</Tag>}
          <Text type="secondary" style={{ fontSize: 12 }}>
            单击任意行可查看请求参数与返回结果
          </Text>
        </Space>
      </Card>

      {/* ---------- 表格 ---------- */}
      <Card variant="borderless" styles={{ body: { padding: 16 } }}>
        <Table<OperLog>
          rowKey="operId"
          loading={loading}
          dataSource={rows}
          size="middle"
          scroll={{ x: 1300 }}
          rowSelection={{
            selectedRowKeys,
            onChange: setSelectedRowKeys,
          }}
          pagination={{
            current: pageNum,
            pageSize: PAGE_SIZE,
            total,
            showSizeChanger: false,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p) => {
              setPageNum(p);
              setSelectedRowKeys([]);
            },
          }}
          onRow={(record) => ({
            onClick: (e) => {
              // 勾选框区域不触发详情
              if ((e.target as HTMLElement).closest('.ant-table-selection-column')) return;
              openDetail(record);
            },
            style: { cursor: 'pointer' },
          })}
          columns={[
            { title: '日志编号', dataIndex: 'operId', width: 100 },
            { title: '系统模块', dataIndex: 'title', width: 150, render: (v: string) => v || '—' },
            {
              title: '操作类型',
              dataIndex: 'businessType',
              width: 100,
              render: (v: number) => (
                <Tag color={BUSINESS_TYPE[v]?.color ?? 'default'}>
                  {BUSINESS_TYPE[v]?.label ?? '其它'}
                </Tag>
              ),
            },
            {
              title: '请求方式',
              dataIndex: 'requestMethod',
              width: 100,
              render: (v: string) => v || '—',
            },
            { title: '操作人员', dataIndex: 'operName', width: 110, render: (v: string) => v || '—' },
            { title: '操作IP', dataIndex: 'operIp', width: 140, render: (v: string) => v || '—' },
            {
              title: '状态',
              dataIndex: 'status',
              width: 90,
              render: (v: number) => (
                <Tag color={v === 1 ? 'success' : 'error'}>{v === 1 ? '成功' : '失败'}</Tag>
              ),
            },
            {
              title: '耗时',
              dataIndex: 'costTime',
              width: 100,
              render: (v: number) => (v == null ? '—' : `${v} ms`),
            },
            {
              title: '操作时间',
              dataIndex: 'operTime',
              width: 180,
              render: (v: string) => v || '—',
            },
            {
              title: '操作',
              key: 'action',
              width: 90,
              fixed: 'right',
              render: (_: unknown, row) => (
                <Button type="link" size="small" icon={<ProfileOutlined />} onClick={() => openDetail(row)}>
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
        title="操作日志详情"
        width={720}
        onClose={() => setDrawerOpen(false)}
        destroyOnHidden
      >
        <Spin spinning={detailLoading}>
          {detail ? (
            <>
              {detail.status === 0 && detail.errorMsg && (
                <Alert
                  type="error"
                  showIcon
                  message="操作失败"
                  description={detail.errorMsg}
                  style={{ marginBottom: 16 }}
                />
              )}

              <Descriptions bordered size="small" column={1} style={{ marginBottom: 16 }}>
                <Descriptions.Item label="日志编号">{detail.operId}</Descriptions.Item>
                <Descriptions.Item label="系统模块">{detail.title || '—'}</Descriptions.Item>
                <Descriptions.Item label="操作类型">
                  <Tag color={BUSINESS_TYPE[detail.businessType ?? 0]?.color ?? 'default'}>
                    {BUSINESS_TYPE[detail.businessType ?? 0]?.label ?? '其它'}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="操作人员">{detail.operName || '—'}</Descriptions.Item>
                <Descriptions.Item label="操作地址">{detail.operIp || '—'}</Descriptions.Item>
                <Descriptions.Item label="请求地址">{detail.operUrl || '—'}</Descriptions.Item>
                <Descriptions.Item label="请求方式">{detail.requestMethod || '—'}</Descriptions.Item>
                <Descriptions.Item label="调用方法">
                  <Text style={{ fontSize: 12 }}>{detail.method || '—'}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="操作状态">
                  <Tag color={detail.status === 1 ? 'success' : 'error'}>
                    {detail.status === 1 ? '成功' : '失败'}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="耗时">
                  {detail.costTime == null ? '—' : `${detail.costTime} ms`}
                </Descriptions.Item>
                <Descriptions.Item label="操作时间">{detail.operTime || '—'}</Descriptions.Item>
              </Descriptions>

              <Paragraph strong style={{ marginBottom: 8 }}>
                请求参数
              </Paragraph>
              <pre
                style={{
                  background: '#FAFAFA',
                  border: '1px solid #F0F0F0',
                  borderRadius: 6,
                  padding: 12,
                  maxHeight: 260,
                  overflow: 'auto',
                  fontSize: 12,
                  lineHeight: 1.6,
                }}
              >
                {prettyJson(detail.operParam) || '无'}
              </pre>

              <Paragraph strong style={{ marginBottom: 8, marginTop: 16 }}>
                返回结果
              </Paragraph>
              <pre
                style={{
                  background: '#FAFAFA',
                  border: '1px solid #F0F0F0',
                  borderRadius: 6,
                  padding: 12,
                  maxHeight: 320,
                  overflow: 'auto',
                  fontSize: 12,
                  lineHeight: 1.6,
                }}
              >
                {prettyJson(detail.jsonResult) || '无'}
              </pre>
            </>
          ) : (
            <Text type="secondary">暂无数据</Text>
          )}
        </Spin>
      </Drawer>
    </div>
  );
}
