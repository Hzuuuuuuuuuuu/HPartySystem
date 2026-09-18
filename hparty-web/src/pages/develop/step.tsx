import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  Collapse,
  Descriptions,
  Empty,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import {
  listStepRules,
  listStages,
  listSteps,
  STAGE_COLORS,
  type DevStage,
  type DevStep,
} from '@/api/develop';

/** 办理角色编码 → 中文 */
const ROLE_LABEL: Record<string, string> = {
  APPLICANT: '本人',
  TRAINER: '培养联系人',
  BRANCH_SECRETARY: '支部书记',
  BRANCH_DEPUTY: '支部副书记',
  ORG_COMMITTEE: '组织委员',
  PROP_COMMITTEE: '宣传委员',
  DISC_COMMITTEE: '纪检委员',
  GROUP_LEADER: '党小组长',
  BRANCH_COMMITTEE: '支部委员会',
  BRANCH_ASSEMBLY: '支部大会',
  PARENT_ORG: '上级党委',
  GRANDPARENT_ORG: '再上一级党委组织部门',
  COUNTY_ORG: '县级党委组织部门',
};

/** 办理组织层级 */
const ORG_TYPE_LABEL: Record<number, string> = {
  1: '党委',
  2: '党总支',
  3: '党支部',
  4: '党小组',
};

/** 步骤类型 */
const STEP_TYPE_LABEL: Record<number, string> = {
  1: '单次办理',
  2: '周期性考察',
};

interface StepRule {
  ruleKey: string;
  description: string;
}

/** 办理角色编码串 → 中文标签数组 */
function roleLabels(codes?: string): string[] {
  if (!codes) return [];
  return codes
    .split(/[,，]/)
    .map((c) => c.trim())
    .filter(Boolean)
    .map((c) => ROLE_LABEL[c] ?? c);
}

/** 拼装期限描述 */
function deadlineText(step: DevStep): string {
  const parts: string[] = [];
  if (step.deadlineDays) parts.push(`办结期限 ${step.deadlineDays} 天`);
  if (step.intervalDays) {
    parts.push(
      step.intervalBaseStep
        ? `距「${step.intervalBaseStep}」满 ${step.intervalDays} 天`
        : `间隔 ${step.intervalDays} 天`,
    );
  }
  if (step.periodicDays) parts.push(`每 ${step.periodicDays} 天考察一次`);
  if (step.minTrainingDays) parts.push(`培训不少于 ${step.minTrainingDays} 天`);
  if (step.minTrainingHours) parts.push(`不少于 ${step.minTrainingHours} 学时`);
  return parts.length ? parts.join('；') : '—';
}

/**
 * 发展流程配置（只读）。
 *
 * 展示 5 个阶段模板与 25 个步骤模板，含办理角色、期限、绑定规则与所需材料；
 * 规则说明按步骤懒加载，避免一次打 25 个请求。
 */
export default function DevelopStepPage() {
  const [loading, setLoading] = useState(true);
  const [stages, setStages] = useState<DevStage[]>([]);
  const [steps, setSteps] = useState<DevStep[]>([]);

  const [rulesMap, setRulesMap] = useState<Record<string, StepRule[]>>({});
  const [ruleLoading, setRuleLoading] = useState<Record<string, boolean>>({});
  const [activeKeys, setActiveKeys] = useState<string[]>([]);

  const fetchAll = useCallback(async () => {
    setLoading(true);
    try {
      const [stageList, stepList] = await Promise.all([listStages(), listSteps()]);
      setStages(stageList ?? []);
      setSteps(stepList ?? []);
      setActiveKeys((stageList ?? []).map((s) => s.stageCode));
      setRulesMap({});
    } catch {
      setStages([]);
      setSteps([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchAll();
  }, [fetchAll]);

  /** 按阶段分组，阶段与步骤均按 order 排序 */
  const grouped = useMemo(() => {
    return [...stages]
      .sort((a, b) => (a.stageOrder ?? 0) - (b.stageOrder ?? 0))
      .map((stage) => ({
        stage,
        steps: steps
          .filter((s) => s.stageCode === stage.stageCode)
          .sort((a, b) => (a.stepOrder ?? 0) - (b.stepOrder ?? 0)),
      }));
  }, [stages, steps]);

  /** 懒加载某步骤的规则说明 */
  const loadRules = useCallback(
    async (stepCode: string) => {
      if (rulesMap[stepCode]) return;
      setRuleLoading((prev) => ({ ...prev, [stepCode]: true }));
      try {
        const rules = await listStepRules(stepCode);
        setRulesMap((prev) => ({ ...prev, [stepCode]: rules ?? [] }));
      } catch {
        setRulesMap((prev) => ({ ...prev, [stepCode]: [] }));
      } finally {
        setRuleLoading((prev) => ({ ...prev, [stepCode]: false }));
      }
    },
    [rulesMap],
  );

  return (
    <div>
      <Card variant="borderless" style={{ marginBottom: 12 }} styles={{ body: { padding: 16 } }}>
        <Space wrap>
          <Button icon={<ReloadOutlined />} onClick={fetchAll}>
            刷新
          </Button>
          <Button onClick={() => setActiveKeys(grouped.map((g) => g.stage.stageCode))}>
            展开全部阶段
          </Button>
          <Button onClick={() => setActiveKeys([])}>收起全部阶段</Button>
          <Typography.Text type="secondary">
            共 {stages.length} 个阶段、{steps.length} 个流程步骤，配置为系统内置只读内容
          </Typography.Text>
        </Space>
      </Card>

      <Spin spinning={loading}>
        {grouped.length === 0 && !loading ? (
          <Card variant="borderless">
            <Empty description="暂无流程模板数据" style={{ padding: '60px 0' }} />
          </Card>
        ) : (
          <Collapse
            activeKey={activeKeys}
            onChange={(keys) => setActiveKeys(keys as string[])}
            items={grouped.map(({ stage, steps: stageSteps }) => {
              const color = STAGE_COLORS[stage.stageCode] ?? '#8C8C8C';
              return {
                key: stage.stageCode,
                label: (
                  <Space size={8}>
                    <Tag color={color}>{stage.stageName}</Tag>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {stage.stageCode} · 共 {stageSteps.length} 步
                    </Typography.Text>
                  </Space>
                ),
                children: (
                  <>
                    {stage.description && (
                      <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
                        {stage.description}
                      </Typography.Paragraph>
                    )}

                    <Collapse
                      accordion={false}
                      onChange={(keys) => {
                        (keys as string[]).forEach((code) => loadRules(code));
                      }}
                      items={stageSteps.map((step) => {
                        const roles = roleLabels(step.handleRoles);
                        const rules = rulesMap[step.stepCode];
                        return {
                          key: step.stepCode,
                          label: (
                            <Space size={8} wrap>
                              <Tag color={color}>第 {step.stepOrder} 步</Tag>
                              <span style={{ fontWeight: 500 }}>{step.stepName}</span>
                              <Tag>{STEP_TYPE_LABEL[step.stepType] ?? '单次办理'}</Tag>
                              {step.needVote === 1 && <Tag color="purple">需表决</Tag>}
                              {step.isBranch === 1 && <Tag color="blue">支部环节</Tag>}
                              {step.ruleKey && <Tag color="gold">绑定规则</Tag>}
                            </Space>
                          ),
                          children: (
                            <Spin spinning={!!ruleLoading[step.stepCode]}>
                              <Descriptions
                                bordered
                                size="small"
                                column={1}
                                labelStyle={{ width: 140 }}
                              >
                                <Descriptions.Item label="步骤编码">
                                  <Typography.Text code>{step.stepCode}</Typography.Text>
                                </Descriptions.Item>

                                <Descriptions.Item label="办理角色">
                                  {roles.length ? (
                                    <Space size={4} wrap>
                                      {roles.map((r) => (
                                        <Tag key={r} color="red">
                                          {r}
                                        </Tag>
                                      ))}
                                    </Space>
                                  ) : (
                                    '—'
                                  )}
                                </Descriptions.Item>

                                <Descriptions.Item label="办理组织层级">
                                  {step.handleOrgType
                                    ? (ORG_TYPE_LABEL[step.handleOrgType] ?? step.handleOrgType)
                                    : '—'}
                                </Descriptions.Item>

                                <Descriptions.Item label="是否需表决">
                                  {step.needVote === 1 ? '是' : '否'}
                                </Descriptions.Item>

                                <Descriptions.Item label="期限与频次">
                                  {deadlineText(step)}
                                </Descriptions.Item>

                                <Descriptions.Item label="绑定规则">
                                  {step.ruleKey ? (
                                    <>
                                      <div style={{ marginBottom: 6 }}>
                                        <Typography.Text code>{step.ruleKey}</Typography.Text>
                                      </div>
                                      {rules === undefined ? (
                                        <Typography.Text type="secondary">
                                          展开后自动加载规则说明…
                                        </Typography.Text>
                                      ) : rules.length === 0 ? (
                                        <Typography.Text type="secondary">
                                          未查询到规则说明
                                        </Typography.Text>
                                      ) : (
                                        <ul style={{ margin: 0, paddingInlineStart: 20 }}>
                                          {rules.map((r) => (
                                            <li key={r.ruleKey}>
                                              <Typography.Text code style={{ fontSize: 12 }}>
                                                {r.ruleKey}
                                              </Typography.Text>
                                              <span style={{ marginInlineStart: 8 }}>
                                                {r.description}
                                              </span>
                                            </li>
                                          ))}
                                        </ul>
                                      )}
                                    </>
                                  ) : (
                                    '—'
                                  )}
                                </Descriptions.Item>

                                <Descriptions.Item label="所需材料">
                                  {step.materialDesc || '—'}
                                </Descriptions.Item>

                                <Descriptions.Item label="流程说明">
                                  {step.description || '—'}
                                </Descriptions.Item>
                              </Descriptions>
                            </Spin>
                          ),
                        };
                      })}
                    />
                  </>
                ),
              };
            })}
          />
        )}
      </Spin>
    </div>
  );
}
