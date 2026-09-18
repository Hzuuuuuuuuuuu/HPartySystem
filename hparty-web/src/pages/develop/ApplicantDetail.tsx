import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Alert,
  Avatar,
  Button,
  Card,
  Col,
  Collapse,
  Descriptions,
  Empty,
  Popconfirm,
  Popover,
  Progress,
  Row,
  Space,
  Spin,
  Tag,
  Tooltip,
  Typography,
  Upload,
  App as AntdApp,
} from 'antd';
import {
  ArrowLeftOutlined,
  CheckCircleFilled,
  ClockCircleFilled,
  CloseCircleFilled,
  DeleteOutlined,
  DownloadOutlined,
  ExclamationCircleFilled,
  EyeOutlined,
  FileTextOutlined,
  FormOutlined,
  UploadOutlined,
  HistoryOutlined,
  InfoCircleOutlined,
  UserOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  deleteApplicantMaterial,
  downloadMaterialTemplate,
  getTimeline,
  selfSubmitApplicantStep,
  STAGE_COLORS,
  uploadApplicantMaterial,
  type DevMaterialTemplateNode,
  type DevStepNode,
  type DevTimeline,
} from '@/api/develop';
import { useUserStore } from '@/store/user';
import { openFilePreview } from '@/utils/filePreview';
import HandleModal from './HandleModal';

const { Paragraph, Text } = Typography;

/** 步骤状态 → 展示配置 */
const STEP_STATUS_META: Record<string, { color: string; icon: React.ReactNode; label: string }> = {
  DONE: { color: '#52C41A', icon: <CheckCircleFilled />, label: '已办结' },
  CURRENT: { color: '#C7000B', icon: <ClockCircleFilled />, label: '待办' },
  PENDING: { color: '#BFBFBF', icon: <ExclamationCircleFilled />, label: '未开始' },
  TERMINATED: { color: '#FF4D4F', icon: <CloseCircleFilled />, label: '已终止' },
};

/** 阶段状态 → 标签色 */
const STAGE_STATUS_TAG: Record<string, { color: string; text: string }> = {
  DONE: { color: 'success', text: '已完成' },
  CURRENT: { color: 'processing', text: '进行中' },
  PENDING: { color: 'default', text: '未开始' },
};

export default function ApplicantDetailPage() {
  const { applicantId } = useParams<{ applicantId: string }>();
  const navigate = useNavigate();
  const { message } = AntdApp.useApp();
  const can = useUserStore((s) => s.can);

  const [loading, setLoading] = useState(true);
  const [timeline, setTimeline] = useState<DevTimeline | null>(null);
  const [handleStep, setHandleStep] = useState<DevStepNode | null>(null);
  const [selfSubmitting, setSelfSubmitting] = useState(false);

  const fetchTimeline = useCallback(async () => {
    if (!applicantId) return;
    setLoading(true);
    try {
      setTimeline(await getTimeline(Number(applicantId)));
    } catch {
      setTimeline(null);
    } finally {
      setLoading(false);
    }
  }, [applicantId]);

  useEffect(() => {
    fetchTimeline();
  }, [fetchTimeline]);

  const submitOwnStep = useCallback(async () => {
    if (!applicantId) return;
    setSelfSubmitting(true);
    try {
      const result = await selfSubmitApplicantStep(Number(applicantId));
      message.success(result.resultText ?? '提交成功');
      result.warnings?.forEach((warning) => message.warning(warning, 6));
      await fetchTimeline();
    } catch {
      // axios 拦截器已统一提示业务错误。
    } finally {
      setSelfSubmitting(false);
    }
  }, [applicantId, fetchTimeline, message]);

  if (loading) {
    return (
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 12,
          padding: '100px 0',
        }}
      >
        <Spin size="large" />
        <span style={{ color: '#8C8C8C' }}>加载中...</span>
      </div>
    );
  }

  if (!timeline) {
    return (
      <Card variant="borderless">
        <Empty description="未找到该发展对象">
          <Button type="primary" onClick={() => navigate('/develop/applicant')}>
            返回列表
          </Button>
        </Empty>
      </Card>
    );
  }

  const stageColor = STAGE_COLORS[timeline.currentStage ?? ''] ?? '#8C8C8C';
  const running = timeline.status === 1;

  return (
    <div>
      {/* ---------- 顶部概览 ---------- */}
      <Card variant="borderless" style={{ marginBottom: 12 }}>
        <Row gutter={24} align="middle">
          <Col flex="none">
            <Avatar
              size={78}
              src={timeline.avatar}
              icon={<UserOutlined />}
              style={{ background: '#C7000B', fontSize: 30 }}
            >
              {timeline.personName?.slice(0, 1)}
            </Avatar>
          </Col>

          <Col flex="auto">
            <Space size={12} align="center" wrap>
              <Text strong style={{ fontSize: 19 }}>
                {timeline.personName}
              </Text>
              <Tag color={stageColor} style={{ borderRadius: 10 }}>
                {timeline.currentStageName}
              </Tag>
              <Tag color={running ? 'processing' : 'default'}>{timeline.statusLabel}</Tag>
              <Text type="secondary">{timeline.orgName}</Text>
            </Space>

            <div style={{ marginTop: 10, maxWidth: 520 }}>
              <Progress
                percent={timeline.progress ?? 0}
                strokeColor={{ from: '#DA1A1A', to: '#C7000B' }}
                size="small"
              />
            </div>

            {(timeline.requiredMaterialCount ?? 0) > 0 && (
              <div style={{ marginTop: 6 }}>
                <Space size={8} wrap>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    材料齐备度
                  </Text>
                  <Text
                    strong
                    style={{
                      fontSize: 12.5,
                      color:
                        (timeline.uploadedMaterialCount ?? 0) >= (timeline.requiredMaterialCount ?? 0)
                          ? '#52C41A'
                          : '#C7000B',
                    }}
                  >
                    {timeline.uploadedMaterialCount ?? 0}/{timeline.requiredMaterialCount}
                  </Text>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    （必备材料，台账按组织归档不计入）
                  </Text>
                </Space>
              </div>
            )}
          </Col>

          <Col flex="none">
            <Space direction="vertical" size={4} style={{ textAlign: 'right' }}>
              <Text type="secondary" style={{ fontSize: 12 }}>
                当前步骤
              </Text>
              <Text strong style={{ color: '#C7000B' }}>
                {timeline.currentStep} {timeline.currentStepName}
              </Text>
              <Button
                size="small"
                type="link"
                icon={<ArrowLeftOutlined />}
                onClick={() => navigate('/develop/applicant')}
                style={{ padding: 0 }}
              >
                返回列表
              </Button>
            </Space>
          </Col>
        </Row>
      </Card>

      {/* ---------- 关键信息 ---------- */}
      <Card variant="borderless" style={{ marginBottom: 12 }} styles={{ body: { paddingBottom: 8 } }}>
        <Descriptions column={{ xs: 1, sm: 2, lg: 4 }} size="small" colon={false}>
          <Descriptions.Item label="递交入党申请书">
            {timeline.applyDate ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label="确定为积极分子">
            {timeline.activistDate ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label="确定为发展对象">
            {timeline.candidateDate ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label="成为预备党员">
            {timeline.probationaryDate ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label="转为正式党员">
            {timeline.fullMemberDate ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label="支部书记">
            {timeline.branchSecretaryName ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label="培养联系人">
            {timeline.trainerNames ?? '—'}
          </Descriptions.Item>
          <Descriptions.Item label="入党介绍人">
            {timeline.introducerNames ?? '—'}
          </Descriptions.Item>
        </Descriptions>

        {(timeline.probationExtendCount ?? 0) > 0 && (
          <Alert
            type="warning"
            showIcon
            style={{ marginTop: 8 }}
            message={`已延长预备期 ${timeline.probationExtendCount} 次（按规定最多延长 1 次）`}
          />
        )}
      </Card>

      {/* ---------- 五阶段 25 步 ---------- */}
      <Card
        variant="borderless"
        title="发展党员流程（5 个阶段 · 25 个步骤）"
        styles={{ body: { paddingTop: 16 } }}
      >
        {timeline.stages.map((stage) => {
          const tag = STAGE_STATUS_TAG[stage.status] ?? STAGE_STATUS_TAG.PENDING;
          return (
            <div className="timeline-stage" key={stage.stageCode}>
              <div className="timeline-stage__header">
                <Tag color={STAGE_COLORS[stage.stageCode]} style={{ borderRadius: 10, marginRight: 0 }}>
                  阶段{stage.stageOrder}
                </Tag>
                <span className="timeline-stage__title">{stage.stageName}</span>
                <Tag color={tag.color}>{tag.text}</Tag>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {stage.doneCount}/{stage.totalCount} 步已办结
                </Text>
              </div>

              {(stage.materialTemplates?.length ?? 0) > 0 && (
                <div style={{ margin: '8px 0 12px 28px' }}>
                  <MaterialTemplateList
                    templates={stage.materialTemplates}
                    applicantId={timeline.applicantId}
                    onRefresh={fetchTimeline}
                    title="阶段材料"
                  />
                </div>
              )}

              {stage.steps.map((step) => (
                <StepRow
                  key={step.stepCode}
                  step={step}
                  applicantId={timeline.applicantId}
                  canHandle={running && step.status === 'CURRENT' && can('develop:applicant:handle')}
                  onHandle={() => setHandleStep(step)}
                  onRefresh={fetchTimeline}
                  onSelfSubmit={submitOwnStep}
                  selfSubmitting={selfSubmitting}
                />
              ))}
            </div>
          );
        })}
      </Card>

      <HandleModal
        open={!!handleStep}
        applicantId={timeline.applicantId}
        personName={timeline.personName}
        step={handleStep}
        onClose={() => setHandleStep(null)}
        onSuccess={(text, warnings) => {
          setHandleStep(null);
          message.success(text ?? '办理成功');
          warnings?.forEach((w) => message.warning(w, 6));
          fetchTimeline();
        }}
      />
    </div>
  );
}

interface StepRowProps {
  step: DevStepNode;
  applicantId: number;
  canHandle: boolean;
  onHandle: () => void;
  onRefresh: () => Promise<void>;
  onSelfSubmit: () => void;
  selfSubmitting: boolean;
}

function StepRow({
  step,
  applicantId,
  canHandle,
  onHandle,
  onRefresh,
  onSelfSubmit,
  selfSubmitting,
}: StepRowProps) {
  const meta = STEP_STATUS_META[step.status] ?? STEP_STATUS_META.PENDING;
  const hasDetail =
    step.opinion ||
    step.content ||
    step.materials?.length ||
    step.history?.length ||
    step.materialTemplates?.length ||
    step.description;

  const head = (
    <div className="timeline-step__head">
      <span className="timeline-step__code">{step.stepCode}</span>
      <span className="timeline-step__name">{step.stepName}</span>
      <Tag color={meta.color} icon={meta.icon} style={{ marginInlineEnd: 0 }}>
        {meta.label}
      </Tag>

      {step.stepType === 2 && <Tag color="blue">周期性考察</Tag>}
      {step.isBranch === 1 && <Tag color="purple">分支节点</Tag>}
      {step.overdue && (
        <Tooltip title={`应于 ${step.deadlineTime} 前办结`}>
          <Tag color="error">已超期</Tag>
        </Tooltip>
      )}

      {step.handleName && (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {step.handleName} · {step.handleTime ? dayjs(step.handleTime).format('YYYY-MM-DD HH:mm') : ''}
        </Text>
      )}

      {canHandle && (
        <Button type="primary" size="small" icon={<FormOutlined />} onClick={onHandle}>
          办理
        </Button>
      )}

      {step.status === 'CURRENT' &&
        (step.canSelfSubmit === true || !!step.selfSubmitBlockedReason) && (
          <Tooltip title={step.canSelfSubmit ? undefined : step.selfSubmitBlockedReason}>
            <span>
              <Button
                type="primary"
                size="small"
                icon={<FormOutlined />}
                disabled={!step.canSelfSubmit}
                loading={selfSubmitting}
                onClick={onSelfSubmit}
              >
                提交本步骤
              </Button>
            </span>
          </Tooltip>
        )}
    </div>
  );

  return (
    <div className="timeline-step">
      {hasDetail ? (
        <Collapse
          ghost
          size="small"
          items={[
            {
              key: step.stepCode,
              label: head,
              children: (
                <StepDetail step={step} applicantId={applicantId} onRefresh={onRefresh} />
              ),
            },
          ]}
        />
      ) : (
        head
      )}
    </div>
  );
}

function StepDetail({
  step,
  applicantId,
  onRefresh,
}: {
  step: DevStepNode;
  applicantId: number;
  onRefresh: () => Promise<void>;
}) {
  return (
    <div className="timeline-step__meta">
      {step.opinion && (
        <div>
          <span className="timeline-step__meta-label">办理意见</span>
          {step.opinion}
        </div>
      )}

      {step.content && (
        <div>
          <span className="timeline-step__meta-label">记录内容</span>
          {step.content}
        </div>
      )}

      {step.resultLabel && step.status === 'DONE' && (
        <div>
          <span className="timeline-step__meta-label">办理结论</span>
          <Tag color={step.result === 1 ? 'success' : 'error'}>{step.resultLabel}</Tag>
        </div>
      )}

      <MaterialTemplateList
        templates={step.materialTemplates}
        materialDesc={step.materialDesc}
        applicantId={applicantId}
        onRefresh={onRefresh}
      />

      {step.rules && step.rules.length > 0 && (
        <div>
          <span className="timeline-step__meta-label">
            <InfoCircleOutlined /> 规则约束
          </span>
          {step.rules.map((r) => (
            <div key={r.ruleKey} style={{ paddingLeft: 14 }}>
              · {r.description}
            </div>
          ))}
        </div>
      )}

      {step.history && step.history.length > 0 && (
        <div style={{ marginTop: 8 }}>
          <span className="timeline-step__meta-label">
            <HistoryOutlined /> 考察记录（{step.history.length} 次）
          </span>
          {step.history.map((h) => (
            <div
              key={h.recordId}
              style={{
                paddingLeft: 14,
                marginBottom: 6,
                borderLeft: '2px solid #F0F0F0',
              }}
            >
              <Text type="secondary" style={{ fontSize: 12 }}>
                第 {h.seqNo} 次 · {h.handleName} ·{' '}
                {h.handleTime ? dayjs(h.handleTime).format('YYYY-MM-DD') : ''}
              </Text>
              {h.content && <div>{h.content}</div>}
              {h.opinion && (
                <Text type="secondary" style={{ fontSize: 12 }}>
                  意见：{h.opinion}
                </Text>
              )}
            </div>
          ))}
        </div>
      )}

      {step.materials && step.materials.length > 0 && (
        <div style={{ marginTop: 6 }}>
          <span className="timeline-step__meta-label">
            <FileTextOutlined /> 已归档材料
          </span>
          <Space wrap size={6}>
            {step.materials.map((m) => (
              <Tag
                key={m.materialId}
                icon={<FileTextOutlined />}
                color="default"
                style={{ cursor: m.fileUrl ? 'pointer' : 'default' }}
                onClick={() => {
                  if (m.fileUrl) void openFilePreview(m.fileUrl).catch(() => undefined);
                }}
              >
                {m.materialName}
                {m.fileUrl && <DownloadOutlined style={{ marginLeft: 4 }} />}
              </Tag>
            ))}
          </Space>
        </div>
      )}

      {step.description && (
        <div style={{ marginTop: 8 }}>
          <span className="timeline-step__meta-label">流程图说明</span>
          <Paragraph
            type="secondary"
            style={{ fontSize: 12.5, marginBottom: 0, whiteSpace: 'pre-wrap' }}
          >
            {step.description}
          </Paragraph>
        </div>
      )}
    </div>
  );
}

/** 材料性质标签：台账 > 必备 > 选填 */
function materialKindTag(m: DevMaterialTemplateNode) {
  if (m.isRoster === 1) return <Tag color="blue">台账</Tag>;
  if (m.isRequired === 1) return <Tag color="red">必备</Tag>;
  return <Tag>选填</Tag>;
}

interface MaterialTemplateListProps {
  templates?: DevMaterialTemplateNode[];
  materialDesc?: string;
  applicantId: number;
  onRefresh: () => Promise<void>;
  title?: string;
}

/**
 * 统一个人材料清单。按钮完全由后端 capability 控制，前端不按角色推断授权。
 */
function MaterialTemplateList({
  templates,
  materialDesc,
  applicantId,
  onRefresh,
  title = '所需材料',
}: MaterialTemplateListProps) {
  const { message } = AntdApp.useApp();
  const [downloading, setDownloading] = useState<string | null>(null);
  const [uploading, setUploading] = useState<number | null>(null);
  const [deleting, setDeleting] = useState<number | null>(null);

  const list = templates ?? [];

  const onDownload = useCallback(
    async (m: DevMaterialTemplateNode, type: 'blank' | 'sample') => {
      const key = `${m.templateId}-${type}`;
      setDownloading(key);
      try {
        await downloadMaterialTemplate(m.templateId, type, `${m.templateName}.doc`);
      } catch (e) {
        message.error(e instanceof Error ? e.message : '模板下载失败，请稍后重试');
      } finally {
        setDownloading(null);
      }
    },
    [message],
  );

  const onUpload = useCallback(
    async (m: DevMaterialTemplateNode, file: File) => {
      setUploading(m.templateId);
      try {
        await uploadApplicantMaterial(applicantId, m.templateId, file);
        message.success(m.repeatable && m.uploaded ? '材料追加上传成功' : m.uploaded ? '材料替换成功' : '材料上传成功');
        await onRefresh();
      } catch {
        // axios 拦截器已提示。
      } finally {
        setUploading(null);
      }
      return false;
    },
    [applicantId, message, onRefresh],
  );

  const onDelete = useCallback(
    async (m: DevMaterialTemplateNode) => {
      if (!m.materialId) return;
      setDeleting(m.materialId);
      try {
        await deleteApplicantMaterial(applicantId, m.materialId);
        message.success('材料已删除');
        await onRefresh();
      } catch {
        // axios 拦截器已提示。
      } finally {
        setDeleting(null);
      }
    },
    [applicantId, message, onRefresh],
  );

  // 没有结构化模板数据时退回原来的纯文字说明
  if (list.length === 0) {
    return materialDesc ? (
      <div>
        <span className="timeline-step__meta-label">{title}</span>
        {materialDesc}
      </div>
    ) : null;
  }

  const uploaded = list.filter((m) => m.uploaded).length;

  return (
    <div style={{ marginTop: 8 }}>
      <span className="timeline-step__meta-label">
        <FileTextOutlined /> {title}（{list.length} 份 · 已上传 {uploaded} 份）
      </span>

      <div className="material-list">
        {list.map((m) => (
          <div key={m.templateId} className="material-item">
            <Tag color="default" style={{ marginInlineEnd: 0, fontVariantNumeric: 'tabular-nums' }}>
              {m.templateCode}
            </Tag>

            <span className="material-item__name">{m.templateName}</span>

            {materialKindTag(m)}

            {m.submitRoleLabel && <Tag color="geekblue">{m.submitRoleLabel}出具</Tag>}

            {m.uploaded ? (
              <Tag color="success" style={{ marginInlineEnd: 0 }}>
                {(m.uploadedCount ?? 0) > 1 ? `已上传 ${m.uploadedCount} 份` : '已上传'}
              </Tag>
            ) : m.isRequired === 1 && m.isRoster !== 1 ? (
              <Tag color="error" style={{ marginInlineEnd: 0 }}>
                待上传
              </Tag>
            ) : null}

            <span className="material-item__actions">
              {m.hasBlank && (
                <Button
                  type="link"
                  size="small"
                  icon={<DownloadOutlined />}
                  loading={downloading === `${m.templateId}-blank`}
                  onClick={() => onDownload(m, 'blank')}
                >
                  下载空表
                </Button>
              )}
              {m.hasSample && (
                <Button
                  type="link"
                  size="small"
                  icon={<FileTextOutlined />}
                  loading={downloading === `${m.templateId}-sample`}
                  onClick={() => onDownload(m, 'sample')}
                >
                  下载样例
                </Button>
              )}
              {m.fillNote && (
                <Popover
                  trigger={['hover', 'click']}
                  placement="topRight"
                  title={`${m.templateCode} ${m.templateName} · 填写说明`}
                  content={
                    <div style={{ maxWidth: 420, fontSize: 12.5, whiteSpace: 'pre-wrap' }}>
                      {m.fillNote}
                    </div>
                  }
                >
                  <Button type="link" size="small" icon={<InfoCircleOutlined />}>
                    填写说明
                  </Button>
                </Popover>
              )}

              {m.canPreview && m.fileUrl && (
                <Button
                  type="link"
                  size="small"
                  icon={<EyeOutlined />}
                  onClick={() => void openFilePreview(m.fileUrl!).catch(() => undefined)}
                >
                  查看
                </Button>
              )}

              {m.canUpload && (
                <Upload
                  showUploadList={false}
                  beforeUpload={(file) => {
                    void onUpload(m, file as File);
                    return false;
                  }}
                >
                  <Button
                    type="link"
                    size="small"
                    icon={<UploadOutlined />}
                    loading={uploading === m.templateId}
                  >
                    {m.repeatable && m.uploaded ? '继续上传' : m.uploaded ? '替换' : '上传'}
                  </Button>
                </Upload>
              )}

              {m.canDelete && m.materialId && (
                <Popconfirm
                  title={`确认删除“${m.templateName}”？`}
                  description="删除后如属必备材料，需要重新上传。"
                  okText="删除"
                  cancelText="取消"
                  onConfirm={() => onDelete(m)}
                >
                  <Button
                    type="link"
                    danger
                    size="small"
                    icon={<DeleteOutlined />}
                    loading={deleting === m.materialId}
                  >
                    删除
                  </Button>
                </Popconfirm>
              )}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
