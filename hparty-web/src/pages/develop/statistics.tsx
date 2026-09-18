import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Card,
  Col,
  Empty,
  Progress,
  Row,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography,
  Button,
} from 'antd';
import { ReloadOutlined, WarningFilled } from '@ant-design/icons';
import {
  getStatistics,
  pageApplicants,
  STAGE_COLORS,
  STAGE_SHORT,
  type DevApplicantCard,
} from '@/api/develop';

const PAGE_SIZE = 10;

interface StageStat {
  stageCode: string;
  stageName: string;
  count: number;
}

/**
 * 发展阶段统计。
 *
 * 顶部 5 个阶段卡片展示在册人数与占比，点击卡片下方列表按该阶段过滤。
 */
export default function DevelopStatisticsPage() {
  const navigate = useNavigate();

  const [loading, setLoading] = useState(false);
  const [statLoading, setStatLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [stages, setStages] = useState<StageStat[]>([]);
  const [activeStage, setActiveStage] = useState<string>('');

  const [rows, setRows] = useState<DevApplicantCard[]>([]);
  const [listTotal, setListTotal] = useState(0);
  const [pageNum, setPageNum] = useState(1);

  /** 阶段统计 */
  const fetchStat = useCallback(async () => {
    setStatLoading(true);
    try {
      const res = await getStatistics();
      setTotal(res?.total ?? 0);
      setStages(res?.stages ?? []);
    } catch {
      setTotal(0);
      setStages([]);
    } finally {
      setStatLoading(false);
    }
  }, []);

  /** 阶段人员列表 */
  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      const res = await pageApplicants({
        pageNum,
        pageSize: PAGE_SIZE,
        currentStage: activeStage || undefined,
      });
      setRows(res.records ?? []);
      setListTotal(res.total ?? 0);
    } catch {
      setRows([]);
      setListTotal(0);
    } finally {
      setLoading(false);
    }
  }, [pageNum, activeStage]);

  useEffect(() => {
    fetchStat();
  }, [fetchStat]);

  useEffect(() => {
    fetchList();
  }, [fetchList]);

  /** 阶段卡片：名称、人数、占比 */
  const stageCards = useMemo(
    () =>
      stages.map((s) => ({
        ...s,
        color: STAGE_COLORS[s.stageCode] ?? '#8C8C8C',
        percent: total > 0 ? Math.round((s.count / total) * 100) : 0,
      })),
    [stages, total],
  );

  const activeStageName =
    stageCards.find((s) => s.stageCode === activeStage)?.stageName ?? '全部阶段';

  return (
    <div>
      {/* ---------- 阶段统计卡片 ---------- */}
      <Card
        variant="borderless"
        style={{ marginBottom: 12 }}
        styles={{ body: { padding: 16 } }}
        title={
          <Space size={8}>
            <span>发展阶段人数</span>
            <Tag color="red">在册合计 {total} 人</Tag>
          </Space>
        }
        extra={
          <Button
            icon={<ReloadOutlined />}
            onClick={() => {
              fetchStat();
              fetchList();
            }}
          >
            刷新
          </Button>
        }
      >
        <Spin spinning={statLoading}>
          {stageCards.length === 0 && !statLoading ? (
            <Empty description="暂无阶段统计数据" style={{ padding: '40px 0' }} />
          ) : (
            <Row gutter={[16, 16]}>
              {stageCards.map((s) => {
                const active = activeStage === s.stageCode;
                return (
                  <Col xs={24} sm={12} lg={8} xl={Math.floor(24 / Math.max(stageCards.length, 1)) || 8} key={s.stageCode}>
                    <div
                      onClick={() => {
                        setActiveStage(active ? '' : s.stageCode);
                        setPageNum(1);
                      }}
                      style={{
                        cursor: 'pointer',
                        border: `1px solid ${active ? s.color : '#F0F0F0'}`,
                        borderTop: `3px solid ${s.color}`,
                        borderRadius: 8,
                        padding: '16px 16px 12px',
                        background: active ? `${s.color}0D` : '#FFFFFF',
                        boxShadow: active ? `0 0 0 3px ${s.color}22` : undefined,
                        transition: 'all .2s',
                      }}
                    >
                      <Statistic
                        title={
                          <Space size={6}>
                            <span>{STAGE_SHORT[s.stageCode] ?? s.stageName}</span>
                            {active && <Tag color={s.color}>筛选中</Tag>}
                          </Space>
                        }
                        value={s.count}
                        suffix="人"
                        valueStyle={{ color: s.color, fontSize: 28 }}
                      />
                      <Progress
                        percent={s.percent}
                        strokeColor={s.color}
                        size="small"
                        format={(p) => `${p ?? 0}%`}
                      />
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        {s.stageName}
                      </Typography.Text>
                    </div>
                  </Col>
                );
              })}
            </Row>
          )}
        </Spin>
      </Card>

      {/* ---------- 阶段人员列表 ---------- */}
      <Card
        variant="borderless"
        styles={{ body: { padding: 16 } }}
        title={
          <Space size={8}>
            <span>{activeStageName}人员</span>
            {activeStage && (
              <Button type="link" size="small" onClick={() => setActiveStage('')}>
                清除筛选
              </Button>
            )}
          </Space>
        }
      >
        <Table<DevApplicantCard>
          rowKey="applicantId"
          loading={loading}
          dataSource={rows}
          size="middle"
          scroll={{ x: 1100 }}
          locale={{ emptyText: <Empty description="该阶段暂无人员" style={{ padding: '40px 0' }} /> }}
          pagination={{
            current: pageNum,
            pageSize: PAGE_SIZE,
            total: listTotal,
            showSizeChanger: false,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (p) => setPageNum(p),
          }}
          columns={[
            { title: '姓名', dataIndex: 'personName', width: 120, render: (v: string) => v || '—' },
            { title: '所属组织', dataIndex: 'orgName', width: 200, ellipsis: true, render: (v: string) => v || '—' },
            {
              title: '当前阶段',
              dataIndex: 'currentStage',
              width: 140,
              render: (v: string, row) => (
                <Tag color={STAGE_COLORS[v] ?? '#8C8C8C'}>
                  {STAGE_SHORT[v] ?? row.currentStageName ?? '—'}
                </Tag>
              ),
            },
            {
              title: '当前步骤',
              dataIndex: 'currentStepName',
              width: 200,
              ellipsis: true,
              render: (v: string) => v || '—',
            },
            {
              title: '进度',
              dataIndex: 'progress',
              width: 160,
              render: (v: number, row) => (
                <Progress
                  percent={v ?? 0}
                  size="small"
                  strokeColor={STAGE_COLORS[row.currentStage ?? ''] ?? '#C7000B'}
                />
              ),
            },
            {
              title: '申请日期',
              dataIndex: 'applyDate',
              width: 130,
              render: (v: string) => v || '—',
            },
            {
              title: '状态',
              dataIndex: 'overdue',
              width: 120,
              render: (v: boolean, row) =>
                v ? (
                  <Tooltip title={`应于 ${row.deadlineTime ?? '—'} 前办结`}>
                    <Tag color="error">
                      <WarningFilled /> 已超期
                    </Tag>
                  </Tooltip>
                ) : (
                  <Tag color="processing">{row.statusLabel ?? '进行中'}</Tag>
                ),
            },
            {
              title: '操作',
              key: 'action',
              width: 100,
              fixed: 'right',
              render: (_: unknown, row) => (
                <Button
                  type="link"
                  size="small"
                  onClick={() => navigate(`/develop/applicant/${row.applicantId}`)}
                >
                  查看 25 步
                </Button>
              ),
            },
          ]}
        />
      </Card>
    </div>
  );
}
