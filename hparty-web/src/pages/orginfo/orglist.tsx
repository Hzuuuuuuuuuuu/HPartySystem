import { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Card, Form, Input, Select, Space, Table, Tag, TreeSelect } from 'antd';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { listDeptTree, listDepts, type SysDept } from '@/api/system';

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

/**
 * 党组织名册（只读）。
 *
 * 与 system/dept 的管理页区分：这里只做全量浏览与查询，不提供增删改。
 */
export default function OrgListPage() {
  const [loading, setLoading] = useState(false);
  const [allDepts, setAllDepts] = useState<SysDept[]>([]);
  const [orgTree, setOrgTree] = useState<OrgTreeNode[]>([]);

  const [searchForm] = Form.useForm();
  const [keyword, setKeyword] = useState('');
  const [typeFilter, setTypeFilter] = useState<number | undefined>(undefined);
  const [orgFilter, setOrgFilter] = useState<number | undefined>(undefined);

  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      setAllDepts(await listDepts());
    } catch {
      setAllDepts([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchList();
    listDeptTree()
      .then((res) => setOrgTree(toOrgTree(res ?? [])))
      .catch(() => setOrgTree([]));
  }, [fetchList]);

  /** orgId → 组织名称，用于展示上级组织 */
  const nameMap = useMemo(() => {
    const map = new Map<number, string>();
    for (const d of allDepts) map.set(d.orgId, d.orgName);
    return map;
  }, [allDepts]);

  /** 客户端筛选，按组织名称 / 类型 / 归属子树 */
  const rows = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    return allDepts.filter((d) => {
      if (kw && !d.orgName.toLowerCase().includes(kw)) return false;
      if (typeFilter !== undefined && d.orgType !== typeFilter) return false;
      if (orgFilter !== undefined) {
        const path = d.orgPath ?? '';
        if (d.orgId !== orgFilter && !path.includes(`/${orgFilter}/`)) return false;
      }
      return true;
    });
  }, [allDepts, keyword, typeFilter, orgFilter]);

  return (
    <div>
      {/* ---------- 工具栏 ---------- */}
      <Card variant="borderless" style={{ marginBottom: 12 }} styles={{ body: { padding: 16 } }}>
        <Form form={searchForm} layout="inline" style={{ rowGap: 12 }}>
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
          <Form.Item name="parentOrg" label="所属上级">
            <TreeSelect
              allowClear
              showSearch
              treeNodeFilterProp="title"
              placeholder="全部"
              treeData={orgTree}
              style={{ width: 220 }}
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
                  setOrgFilter(v.parentOrg ?? undefined);
                }}
              >
                查询
              </Button>
              <Button
                onClick={() => {
                  searchForm.resetFields();
                  setKeyword('');
                  setTypeFilter(undefined);
                  setOrgFilter(undefined);
                }}
              >
                重置
              </Button>
              <Button icon={<ReloadOutlined />} onClick={fetchList}>
                刷新
              </Button>
            </Space>
          </Form.Item>
          {rows.length > 0 && (
            <Form.Item>
              <Tag color="red">共 {rows.length} 个党组织</Tag>
            </Form.Item>
          )}
        </Form>
      </Card>

      {/* ---------- 名册表格 ---------- */}
      <Card variant="borderless" styles={{ body: { padding: 16 } }}>
        <Table<SysDept>
          rowKey="orgId"
          loading={loading}
          dataSource={rows}
          size="middle"
          scroll={{ x: 1300 }}
          pagination={{ pageSize: 10, showSizeChanger: false, showTotal: (t) => `共 ${t} 条` }}
          columns={[
            { title: '组织名称', dataIndex: 'orgName', width: 240, fixed: 'left' },
            {
              title: '上级组织',
              dataIndex: 'parentId',
              width: 200,
              ellipsis: true,
              render: (v: number) => (v ? (nameMap.get(v) ?? '—') : '—'),
            },
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
              sorter: (a, b) => (a.memberCount ?? 0) - (b.memberCount ?? 0),
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
          ]}
        />
      </Card>
    </div>
  );
}
