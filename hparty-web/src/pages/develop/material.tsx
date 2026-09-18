import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  Collapse,
  Empty,
  Input,
  Popover,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd';
import { DownloadOutlined, FileTextOutlined, ReloadOutlined } from '@ant-design/icons';
import {
  downloadMaterialTemplate,
  listMaterialTemplates,
  listStages,
  listSteps,
  STAGE_COLORS,
  type DevMaterialTemplate,
  type DevStage,
  type DevStep,
} from '@/api/develop';

/** 材料出具方编码 → 中文（与后端 DevMaterialTemplateService 保持一致） */
const SUBMIT_ROLE_LABEL: Record<string, string> = {
  APPLICANT: '本人',
  BRANCH: '党支部',
  TRAINER: '培养联系人',
  PARENT_ORG: '上级党委',
};

/** 阶段编码 → 阶段名兜底（拉不到 /develop/flow/stages 时用） */
const STAGE_NAME_FALLBACK: Record<string, string> = {
  STAGE_1: '申请入党',
  STAGE_2: '入党积极分子的确定和培养教育',
  STAGE_3: '发展对象的确定和考察',
  STAGE_4: '预备党员的接收',
  STAGE_5: '预备党员的教育考察和转正',
};

/**
 * 发展党员材料模板库（只读）。
 *
 * <p>按 5 个阶段分组展示《广西发展党员工作手册》的 50 份表格，
 * 每份可下载空白模板与填写样例（有则显示）。</p>
 */
export default function DevelopMaterialPage() {
  const { message } = AntdApp.useApp();

  const [loading, setLoading] = useState(true);
  const [templates, setTemplates] = useState<DevMaterialTemplate[]>([]);
  const [stages, setStages] = useState<DevStage[]>([]);
  const [steps, setSteps] = useState<DevStep[]>([]);
  const [keyword, setKeyword] = useState('');
  const [activeKeys, setActiveKeys] = useState<string[]>([]);
  const [downloading, setDownloading] = useState<string | null>(null);

  const fetchAll = useCallback(async () => {
    setLoading(true);
    try {
      const list = await listMaterialTemplates();
      setTemplates(list ?? []);
    } catch {
      setTemplates([]);
    }
    // 阶段/步骤名称只是锦上添花，缺权限时降级显示编码，不影响材料浏览与下载
    try {
      setStages((await listStages()) ?? []);
    } catch {
      setStages([]);
    }
    try {
      setSteps((await listSteps()) ?? []);
    } catch {
      setSteps([]);
    }
    setLoading(false);
  }, []);

  useEffect(() => {
    fetchAll();
  }, [fetchAll]);

  const stepNameMap = useMemo(() => {
    const map: Record<string, string> = {};
    steps.forEach((s) => {
      map[s.stepCode] = s.stepName;
    });
    return map;
  }, [steps]);

  /** 关键字过滤：编号 / 名称 / 填写说明 */
  const filtered = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    if (!kw) return templates;
    return templates.filter(
      (t) =>
        t.templateCode?.toLowerCase().includes(kw) ||
        t.templateName?.toLowerCase().includes(kw) ||
        t.fillNote?.toLowerCase().includes(kw),
    );
  }, [templates, keyword]);

  /** 按阶段分组，阶段顺序取后端模板；后端拿不到时按材料数据里出现过的阶段兜底 */
  const grouped = useMemo(() => {
    const ordered = [...stages].sort((a, b) => (a.stageOrder ?? 0) - (b.stageOrder ?? 0));
    const codes = ordered.length
      ? ordered.map((s) => s.stageCode)
      : Array.from(new Set(templates.map((t) => t.stageCode))).sort();

    return codes
      .map((code) => {
        const stage = ordered.find((s) => s.stageCode === code);
        return {
          stageCode: code,
          stageName: stage?.stageName ?? STAGE_NAME_FALLBACK[code] ?? code,
          description: stage?.description,
          list: filtered.filter((t) => t.stageCode === code),
        };
      })
      .filter((g) => g.list.length > 0);
  }, [stages, templates, filtered]);

  const expandAll = useCallback(() => setActiveKeys(grouped.map((g) => g.stageCode)), [grouped]);

  useEffect(() => {
    // 首次加载完成后默认展开全部阶段
    if (grouped.length && activeKeys.length === 0) {
      setActiveKeys(grouped.map((g) => g.stageCode));
    }
  }, [grouped, activeKeys.length]);

  const onDownload = useCallback(
    async (t: DevMaterialTemplate, type: 'blank' | 'sample') => {
      const key = `${t.templateId}-${type}`;
      setDownloading(key);
      try {
        await downloadMaterialTemplate(t.templateId, type, `${t.templateName}.doc`);
      } catch (e) {
        message.error(e instanceof Error ? e.message : '模板下载失败，请稍后重试');
      } finally {
        setDownloading(null);
      }
    },
    [message],
  );

  const requiredCount = templates.filter((t) => t.isRequired === 1).length;
  const sampleCount = templates.filter((t) => !!t.sampleFile).length;

  return (
    <div>
      <Card variant="borderless" style={{ marginBottom: 12 }} styles={{ body: { padding: 16 } }}>
        <Space wrap>
          <Button icon={<ReloadOutlined />} onClick={fetchAll}>
            刷新
          </Button>
          <Button onClick={expandAll}>展开全部阶段</Button>
          <Button onClick={() => setActiveKeys([])}>收起全部阶段</Button>
          <Input.Search
            allowClear
            placeholder="按编号 / 名称 / 填写说明搜索"
            style={{ width: 260 }}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
          <Typography.Text type="secondary">
            共 {templates.length} 份表格，其中必备 {requiredCount} 份、提供填写样例 {sampleCount} 份
          </Typography.Text>
        </Space>
      </Card>

      <Spin spinning={loading}>
        {grouped.length === 0 && !loading ? (
          <Card variant="borderless">
            <Empty description="暂无材料模板数据" style={{ padding: '60px 0' }} />
          </Card>
        ) : (
          <Collapse
            activeKey={activeKeys}
            onChange={(keys) => setActiveKeys(keys as string[])}
            items={grouped.map((g) => ({
              key: g.stageCode,
              label: (
                <Space size={8} wrap>
                  <Tag color={STAGE_COLORS[g.stageCode] ?? '#8C8C8C'}>{g.stageName}</Tag>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    {g.stageCode} · 共 {g.list.length} 份材料
                  </Typography.Text>
                </Space>
              ),
              children: (
                <>
                  {g.description && (
                    <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
                      {g.description}
                    </Typography.Paragraph>
                  )}

                  <Table<DevMaterialTemplate>
                    rowKey="templateId"
                    dataSource={g.list}
                    size="middle"
                    pagination={false}
                    scroll={{ x: 1000 }}
                    locale={{ emptyText: <Empty description="无匹配材料" /> }}
                    columns={[
                      {
                        title: '编号',
                        dataIndex: 'templateCode',
                        width: 100,
                        render: (v: string) => <Typography.Text code>{v}</Typography.Text>,
                      },
                      {
                        title: '材料名称',
                        dataIndex: 'templateName',
                        ellipsis: true,
                        render: (v: string) => v || '—',
                      },
                      {
                        title: '性质',
                        dataIndex: 'isRequired',
                        width: 130,
                        render: (_: number, row) =>
                          row.isRoster === 1 ? (
                            <Tag color="blue">台账</Tag>
                          ) : row.isRequired === 1 ? (
                            <Tag color="red">必备</Tag>
                          ) : (
                            <Tag>选填</Tag>
                          ),
                      },
                      {
                        title: '出具方',
                        dataIndex: 'submitRole',
                        width: 120,
                        render: (v: string) => (
                          <Tag color="geekblue">{SUBMIT_ROLE_LABEL[v] ?? v ?? '—'}</Tag>
                        ),
                      },
                      {
                        title: '关联步骤',
                        dataIndex: 'stepCode',
                        width: 210,
                        ellipsis: true,
                        render: (v: string) =>
                          v ? (
                            <>
                              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                                {v}
                              </Typography.Text>{' '}
                              {stepNameMap[v] ?? ''}
                            </>
                          ) : (
                            <Typography.Text type="secondary">阶段/全程通用</Typography.Text>
                          ),
                      },
                      {
                        title: '填写说明',
                        dataIndex: 'fillNote',
                        width: 120,
                        render: (v: string) =>
                          v ? (
                            <Popover
                              trigger={['hover', 'click']}
                              placement="topRight"
                              title="填写说明"
                              content={
                                <div
                                  style={{ maxWidth: 420, fontSize: 12.5, whiteSpace: 'pre-wrap' }}
                                >
                                  {v}
                                </div>
                              }
                            >
                              <Button type="link" size="small">
                                查看说明
                              </Button>
                            </Popover>
                          ) : (
                            '—'
                          ),
                      },
                      {
                        title: '操作',
                        key: 'action',
                        width: 190,
                        fixed: 'right',
                        render: (_: unknown, row) => (
                          <Space size={0}>
                            {row.blankFile ? (
                              <Button
                                type="link"
                                size="small"
                                icon={<DownloadOutlined />}
                                loading={downloading === `${row.templateId}-blank`}
                                onClick={() => onDownload(row, 'blank')}
                              >
                                下载空表
                              </Button>
                            ) : (
                              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                                无空表
                              </Typography.Text>
                            )}
                            {row.sampleFile && (
                              <Button
                                type="link"
                                size="small"
                                icon={<FileTextOutlined />}
                                loading={downloading === `${row.templateId}-sample`}
                                onClick={() => onDownload(row, 'sample')}
                              >
                                下载样例
                              </Button>
                            )}
                          </Space>
                        ),
                      },
                    ]}
                  />
                </>
              ),
            }))}
          />
        )}
      </Spin>
    </div>
  );
}
