import { useCallback, useEffect, useState } from 'react';
import type { Key } from 'react';
import {
  App as AntdApp,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Select,
  Space,
  Table,
  Tag,
} from 'antd';
import {
  ClearOutlined,
  DeleteOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import type { Dayjs } from 'dayjs';
import {
  cleanLoginLogs,
  pageLoginLogs,
  removeLoginLogs,
  type LoginLog,
  type LoginLogQuery,
} from '@/api/system';
import { useUserStore } from '@/store/user';

const PAGE_SIZE = 10;
const { RangePicker } = DatePicker;

/**
 * 后端 beginTime / endTime 是 LocalDateTime，
 * Spring Boot 默认转换器只认 ISO 格式（yyyy-MM-ddTHH:mm:ss），
 * 空格分隔的写法会直接 400，这里统一按天展开成 ISO 串。
 */
function toIso(date: Dayjs, endOfDay = false) {
  return `${date.format('YYYY-MM-DD')}T${endOfDay ? '23:59:59' : '00:00:00'}`;
}

/** 登录日志 */
export default function LoginLogPage() {
  const { message, modal } = AntdApp.useApp();
  const can = useUserStore((s) => s.can);

  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<LoginLog[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNum, setPageNum] = useState(1);
  const [selectedRowKeys, setSelectedRowKeys] = useState<Key[]>([]);

  const [searchForm] = Form.useForm();
  const [query, setQuery] = useState<LoginLogQuery>({});

  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      const res = await pageLoginLogs({ ...query, pageNum, pageSize: PAGE_SIZE });
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
      username: values.username || undefined,
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

  const confirmRemove = () => {
    if (!selectedRowKeys.length) return;
    modal.confirm({
      title: `确认删除选中的 ${selectedRowKeys.length} 条登录日志？`,
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await removeLoginLogs(selectedRowKeys.join(','));
        message.success('删除成功');
        setSelectedRowKeys([]);
        fetchList();
      },
    });
  };

  const confirmClean = () => {
    modal.confirm({
      title: '确认清空全部登录日志？',
      content: '清空后数据不可恢复，请谨慎操作。',
      okText: '确认清空',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await cleanLoginLogs();
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
          <Form.Item name="username" label="登录账号">
            <Input allowClear placeholder="登录账号" style={{ width: 150 }} onPressEnter={doSearch} />
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
          <Form.Item name="range" label="登录时间">
            <RangePicker style={{ width: 260 }} />
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
          {can('system:loginlog:remove') && (
            <Button icon={<DeleteOutlined />} disabled={!selectedRowKeys.length} onClick={confirmRemove}>
              删除
            </Button>
          )}
          {can('system:loginlog:clean') && (
            <Button icon={<ClearOutlined />} danger onClick={confirmClean}>
              清空
            </Button>
          )}
          <Button icon={<ReloadOutlined />} onClick={fetchList}>
            刷新
          </Button>
          {selectedRowKeys.length > 0 && <Tag color="red">已选 {selectedRowKeys.length} 条</Tag>}
        </Space>
      </Card>

      {/* ---------- 表格 ---------- */}
      <Card variant="borderless" styles={{ body: { padding: 16 } }}>
        <Table<LoginLog>
          rowKey="infoId"
          loading={loading}
          dataSource={rows}
          size="middle"
          scroll={{ x: 1200 }}
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
          columns={[
            { title: '访问编号', dataIndex: 'infoId', width: 100 },
            { title: '登录账号', dataIndex: 'username', width: 130, render: (v: string) => v || '—' },
            { title: '登录IP', dataIndex: 'ipaddr', width: 140, render: (v: string) => v || '—' },
            {
              title: '登录地点',
              dataIndex: 'loginLocation',
              width: 150,
              render: (v: string) => v || '—',
            },
            { title: '浏览器', dataIndex: 'browser', width: 130, render: (v: string) => v || '—' },
            { title: '操作系统', dataIndex: 'os', width: 130, render: (v: string) => v || '—' },
            {
              title: '状态',
              dataIndex: 'status',
              width: 90,
              render: (v: number) => (
                <Tag color={v === 1 ? 'success' : 'error'}>{v === 1 ? '成功' : '失败'}</Tag>
              ),
            },
            { title: '提示信息', dataIndex: 'msg', ellipsis: true, render: (v: string) => v || '—' },
            {
              title: '登录时间',
              dataIndex: 'loginTime',
              width: 180,
              render: (v: string) => v || '—',
            },
          ]}
        />
      </Card>
    </div>
  );
}
