import { useEffect, useState } from 'react';
import { Button, Card, Col, Empty, Row, Spin, Upload, App as AntdApp } from 'antd';
import { UploadOutlined } from '@ant-design/icons';
import { listTasks, submitTaskMaterial, type AmTask } from '@/api/task';

/**
 * 活动任务通知（还原图1 底部的四张任务卡）。
 *
 * 每张卡展示发布单位、活动名称、活动内容、活动时间，右下角提供「上传资料」按钮。
 * 只展示「已发布」的任务，故固定 status=1。
 */
export default function TaskNoticeList() {
  const { message } = AntdApp.useApp();
  const [loading, setLoading] = useState(false);
  const [tasks, setTasks] = useState<AmTask[]>([]);

  const fetchTasks = async () => {
    setLoading(true);
    try {
      const res = await listTasks({ status: 1 });
      setTasks(res ?? []);
    } catch {
      setTasks([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTasks();
  }, []);

  const upload = async (taskId: number, file: File) => {
    try {
      await submitTaskMaterial(taskId, file);
      message.success('资料上传成功');
      fetchTasks();
    } catch {
      // 拦截器已提示
    }
    return false;
  };

  return (
    <Card variant="borderless" title="活动任务通知" styles={{ body: { padding: 16 } }}>
      <Spin spinning={loading}>
        {tasks.length === 0 && !loading ? (
          <Empty description="暂无活动任务" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <Row gutter={[14, 14]}>
            {tasks.map((task) => (
              <Col xs={24} lg={12} key={task.taskId}>
                <div className="task-card">
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div className="task-card__field">
                      <span className="task-card__label">发布单位：</span>
                      {task.publishOrgName ?? '—'}
                    </div>
                    <div className="task-card__field">
                      <span className="task-card__label">活动名称：</span>
                      {task.activityName ?? task.title}
                    </div>
                    <div className="task-card__field">
                      <span className="task-card__label">活动内容：</span>
                      {task.content ?? '—'}
                    </div>
                    <div className="task-card__field">
                      <span className="task-card__label">活动时间：</span>
                      {task.startDate ?? '—'} ~ {task.endDate ?? '—'}
                    </div>
                  </div>

                  <Upload
                    showUploadList={false}
                    beforeUpload={(file) => upload(task.taskId, file)}
                  >
                    <Button type="primary" icon={<UploadOutlined />}>
                      上传资料
                    </Button>
                  </Upload>
                </div>
              </Col>
            ))}
          </Row>
        )}
      </Spin>
    </Card>
  );
}
