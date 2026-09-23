import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Badge, Card, Col, Progress, Row, Statistic, Tag, Spin, Empty } from 'antd';
import {
  BellOutlined,
  TeamOutlined,
  UserAddOutlined,
  ApartmentOutlined,
  RiseOutlined,
} from '@ant-design/icons';
import logo from '@/assets/logo.png';
import { getStatistics, STAGE_COLORS, type DevApplicantCard } from '@/api/develop';
import { pageApplicants } from '@/api/develop';
import { getPersonStatistics } from '@/api/system';
import { getMyTodoCount, type TodoResult } from '@/api/todo';
import { useUserStore } from '@/store/user';

export default function DashboardPage() {
  const navigate = useNavigate();
  const userInfo = useUserStore((s) => s.userInfo);
  const can = useUserStore((s) => s.can);

  const canDevStat = can('develop:stat:list');
  const canApplicantList = can('develop:applicant:list');
  const canPersonStat =
    can('system:person:list') || can('orginfo:member:list') || can('orginfo:tree');

  const [loading, setLoading] = useState(true);
  const [devStat, setDevStat] = useState<{ total: number; stages: { stageCode: string; stageName: string; count: number }[] } | null>(null);
  const [personStat, setPersonStat] = useState<Record<string, any> | null>(null);
  const [recent, setRecent] = useState<DevApplicantCard[]>([]);
  const [todo, setTodo] = useState<TodoResult | null>(null);

  useEffect(() => {
    const requests: Promise<unknown>[] = [];

    if (canDevStat) {
      requests.push(getStatistics().then(setDevStat));
    }
    if (canPersonStat) {
      requests.push(
        getPersonStatistics().then((value) => setPersonStat(value as Record<string, any>)),
      );
    }
    if (canApplicantList) {
      requests.push(
        pageApplicants({ pageNum: 1, pageSize: 6 }).then((value) =>
          setRecent(value.records ?? []),
        ),
      );
    }
    requests.push(getMyTodoCount().then(setTodo));

    Promise.allSettled(requests).finally(() => setLoading(false));
  }, [canApplicantList, canDevStat, canPersonStat]);

  return (
    <Spin spinning={loading}>
      {/* 欢迎条 */}
      <Card
        variant="borderless"
        style={{ marginBottom: 12, background: 'linear-gradient(120deg,#C7000B 0%,#8F0007 100%)' }}
        styles={{ body: { padding: '22px 26px' } }}
      >
        <Row align="middle" gutter={16}>
          <Col flex="none">
            <div
              style={{
                width: 52,
                height: 52,
                borderRadius: '50%',
                background: '#fff',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <img src={logo} alt="logo" style={{ width: 44, height: 44, objectFit: 'contain' }} />
            </div>
          </Col>
          <Col flex="auto">
            <div style={{ color: '#fff', fontSize: 19, fontWeight: 600 }}>
              {userInfo?.nickName ?? userInfo?.username}，欢迎回来
            </div>
            <div style={{ color: 'rgba(255,255,255,.82)', fontSize: 13, marginTop: 4 }}>
              {userInfo?.orgName ?? '未分配党组织'}
              {userInfo?.personName ? ` · ${userInfo.personName}` : ''}
            </div>
          </Col>
          <Col flex="none">
            <Tag color="rgba(255,255,255,.2)" style={{ color: '#fff', border: 'none', borderRadius: 10 }}>
              智慧党建
            </Tag>
          </Col>
        </Row>
      </Card>

      {/* 指标卡：5 张，用 grid 自适应排列（antd 的 24 栅格除不尽 5） */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))',
          gap: 12,
          marginBottom: 12,
        }}
      >
        {/* 我的待办：点击进入 /my-todo，有待办时显示红点 */}
        <Card
          variant="borderless"
          hoverable
          onClick={() => navigate('/my-todo')}
          style={{ cursor: 'pointer' }}
        >
          <Badge count={todo?.overdueTotal ?? 0} size="small" offset={[4, 2]}>
            <Statistic
              title="我的待办"
              value={todo?.total ?? '—'}
              prefix={<BellOutlined style={{ color: '#C7000B' }} />}
              suffix={
                todo?.overdueTotal ? (
                  <span style={{ fontSize: 12, color: '#FF4D4F' }}>
                    {todo.overdueTotal} 项超期
                  </span>
                ) : undefined
              }
            />
          </Badge>
        </Card>
        <Card variant="borderless">
          <Statistic
            title="党员总数"
            value={personStat?.memberCount ?? personStat?.member_count ?? '—'}
            prefix={<TeamOutlined style={{ color: '#C7000B' }} />}
          />
        </Card>
        <Card variant="borderless">
          <Statistic
            title="发展党员进行中"
            value={devStat?.total ?? '—'}
            prefix={<UserAddOutlined style={{ color: '#FAAD14' }} />}
          />
        </Card>
        <Card variant="borderless">
          <Statistic
            title="党组织数"
            value={personStat?.orgCount ?? '—'}
            prefix={<ApartmentOutlined style={{ color: '#1890FF' }} />}
          />
        </Card>
        <Card variant="borderless">
          <Statistic
            title="预备党员"
            value={personStat?.probationaryCount ?? personStat?.probationary_count ?? '—'}
            prefix={<RiseOutlined style={{ color: '#722ED1' }} />}
          />
        </Card>
      </div>

      <Row gutter={12}>
        {/* 各阶段分布 */}
        <Col xs={24} lg={12}>
          <Card variant="borderless" title="发展党员阶段分布" style={{ marginBottom: 12 }}>
            {devStat && devStat.stages.length > 0 ? (
              devStat.stages.map((s) => {
                const percent =
                  devStat.total > 0 ? Math.round((s.count / devStat.total) * 100) : 0;
                return (
                  <div key={s.stageCode} style={{ marginBottom: 14 }}>
                    <div
                      style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        fontSize: 13,
                        marginBottom: 4,
                      }}
                    >
                      <span>{s.stageName}</span>
                      <span style={{ color: '#8C8C8C' }}>{s.count} 人</span>
                    </div>
                    <Progress
                      percent={percent}
                      showInfo={false}
                      strokeColor={STAGE_COLORS[s.stageCode]}
                      size="small"
                    />
                  </div>
                );
              })
            ) : (
              <Empty description="暂无数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            )}
          </Card>
        </Col>

        {/* 最近发展对象 */}
        <Col xs={24} lg={12}>
          <Card
            variant="borderless"
            title="最近发展对象"
            extra={<a onClick={() => navigate('/develop/applicant')}>查看全部</a>}
          >
            {recent.length > 0 ? (
              recent.map((r) => (
                <div
                  key={r.applicantId}
                  onClick={() => navigate(`/develop/applicant/${r.applicantId}`)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 12,
                    padding: '9px 8px',
                    borderRadius: 8,
                    cursor: 'pointer',
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.background = '#FFF5F5')}
                  onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
                >
                  <div
                    style={{
                      width: 36,
                      height: 36,
                      borderRadius: '50%',
                      background: '#C7000B',
                      color: '#fff',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontSize: 15,
                      flex: 'none',
                    }}
                  >
                    {r.personName?.slice(0, 1)}
                  </div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 14, fontWeight: 500 }}>{r.personName}</div>
                    <div style={{ fontSize: 12, color: '#8C8C8C' }}>
                      {r.orgName} · {r.currentStepName}
                    </div>
                  </div>
                  <Tag color={STAGE_COLORS[r.currentStage ?? '']} style={{ borderRadius: 10 }}>
                    {r.currentStageName}
                  </Tag>
                </div>
              ))
            ) : (
              <Empty description="暂无数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            )}
          </Card>
        </Col>
      </Row>
    </Spin>
  );
}
