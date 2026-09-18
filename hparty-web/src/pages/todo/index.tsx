import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Badge, Button, Card, Empty, List, Space, Spin, Tag, App as AntdApp } from 'antd';
import {
  BellOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  ReloadOutlined,
  RightOutlined,
} from '@ant-design/icons';
import { getMyTodo, TODO_GROUP_COLORS, type TodoItem, type TodoResult } from '@/api/todo';
import { useUserStore } from '@/store/user';

/**
 * 我的待办。
 *
 * <p>聚合发展党员 / 三会一课任务 / 党费 / 组织关系转接四个来源的待办。
 * 数据范围由后端按当前登录用户的数据权限裁剪 —— 支部书记看到本支部的，
 * 普通党员只看到与自己相关的，前端不做二次过滤。</p>
 */
export default function TodoPage() {
  const navigate = useNavigate();
  const { message } = AntdApp.useApp();
  const userInfo = useUserStore((s) => s.userInfo);

  const [loading, setLoading] = useState(true);
  const [data, setData] = useState<TodoResult | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await getMyTodo());
    } catch {
      // 错误提示由 axios 拦截器统一处理
      setData(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const openItem = (item: TodoItem) => {
    if (!item.link) {
      message.info('该待办暂无可跳转的详情页');
      return;
    }
    navigate(item.link);
  };

  const groups = data?.groups ?? [];
  const nonEmpty = groups.filter((g) => (g.items?.length ?? 0) > 0);

  return (
    <Spin spinning={loading}>
      <Card
        variant="borderless"
        style={{ marginBottom: 12, background: 'linear-gradient(120deg,#C7000B 0%,#8F0007 100%)' }}
        styles={{ body: { padding: '20px 24px' } }}
      >
        <Space size={16} align="center" wrap>
          <BellOutlined style={{ fontSize: 30, color: '#FFD700' }} />
          <div>
            <div style={{ color: '#fff', fontSize: 18, fontWeight: 600 }}>
              {userInfo?.nickName ?? userInfo?.username}，你有 {data?.total ?? 0} 项待办
            </div>
            <div style={{ color: 'rgba(255,255,255,.82)', fontSize: 13, marginTop: 4 }}>
              {data?.overdueTotal
                ? `其中 ${data.overdueTotal} 项已超期，请尽快办理`
                : '暂无超期事项'}
            </div>
          </div>
          <Button ghost icon={<ReloadOutlined />} onClick={load}>
            刷新
          </Button>
        </Space>
      </Card>

      {nonEmpty.length === 0 && !loading ? (
        <Card variant="borderless">
          <Empty
            image={<CheckCircleOutlined style={{ fontSize: 56, color: '#52C41A' }} />}
            description="当前没有待办事项"
            style={{ padding: '48px 0' }}
          />
        </Card>
      ) : (
        nonEmpty.map((group) => (
          <Card
            key={group.type}
            variant="borderless"
            style={{ marginBottom: 12 }}
            styles={{ body: { padding: 0 } }}
            title={
              <Space>
                <Badge color={TODO_GROUP_COLORS[group.type] ?? '#8C8C8C'} />
                <span>{group.typeLabel}</span>
                <Tag color={TODO_GROUP_COLORS[group.type] ?? 'default'}>{group.count}</Tag>
              </Space>
            }
          >
            <List
              dataSource={group.items ?? []}
              rowKey={(item) => item.key}
              renderItem={(item) => (
                <List.Item
                  onClick={() => openItem(item)}
                  style={{ padding: '12px 20px', cursor: 'pointer' }}
                  className="todo-item"
                >
                  <List.Item.Meta
                    avatar={
                      item.overdue ? (
                        <ClockCircleOutlined style={{ fontSize: 18, color: '#FF4D4F' }} />
                      ) : (
                        <ClockCircleOutlined style={{ fontSize: 18, color: '#BFBFBF' }} />
                      )
                    }
                    title={
                      <Space size={8} wrap>
                        <span style={{ color: item.overdue ? '#FF4D4F' : undefined }}>
                          {item.title}
                        </span>
                        {item.overdue && (
                          <Tag color="error">已超期 {item.overdueDays ?? 0} 天</Tag>
                        )}
                      </Space>
                    }
                    description={
                      <span style={{ fontSize: 12, color: '#8C8C8C' }}>{item.description}</span>
                    }
                  />
                  <RightOutlined style={{ color: '#BFBFBF' }} />
                </List.Item>
              )}
            />
          </Card>
        ))
      )}
    </Spin>
  );
}
