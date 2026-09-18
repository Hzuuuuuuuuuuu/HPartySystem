import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Col,
  DatePicker,
  Divider,
  Form,
  Input,
  InputNumber,
  Modal,
  Radio,
  Row,
  Select,
  Space,
  Tag,
  Typography,
  App as AntdApp,
} from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined, UndoOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { handleStep, previewStep, type DevHandleForm, type DevStepNode } from '@/api/develop';
import { listPersonOptions, type PersonOption } from '@/api/system';

const { Text } = Typography;
const { TextArea } = Input;

interface Props {
  open: boolean;
  applicantId: number;
  personName?: string;
  step: DevStepNode | null;
  onClose: () => void;
  onSuccess: (text?: string, warnings?: string[]) => void;
}

/** 需要填写表决数据的步骤 */
const VOTE_STEPS = ['STEP_15', 'STEP_23'];
/** 需要指定培养联系人的步骤 */
const TRAINER_STEPS = ['STEP_05'];
/** 需要指定入党介绍人的步骤 */
const INTRODUCER_STEPS = ['STEP_09'];

export default function HandleModal({
  open,
  applicantId,
  personName,
  step,
  onClose,
  onSuccess,
}: Props) {
  const { message } = AntdApp.useApp();
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const [preview, setPreview] = useState<string[]>([]);
  const [persons, setPersons] = useState<PersonOption[]>([]);

  const stepCode = step?.stepCode ?? '';
  const isVote = VOTE_STEPS.includes(stepCode);
  const isBranch = step?.isBranch === 1;
  const isPeriodic = step?.stepType === 2;
  const isTraining = stepCode === 'STEP_11';
  const isTalk = stepCode === 'STEP_02' || stepCode === 'STEP_16';
  const isPolitical = stepCode === 'STEP_10';

  const result = Form.useWatch('result', form) ?? 1;

  useEffect(() => {
    if (open) {
      form.resetFields();
      form.setFieldsValue({
        result: 1,
        advance: !isPeriodic,
        vote: { opposeCount: 0, abstainCount: 0 },
        training: { isQualified: 1 },
        politicalReview: { reviewResult: 1 },
      });
      setPreview([]);
      if (TRAINER_STEPS.includes(stepCode) || INTRODUCER_STEPS.includes(stepCode)) {
        listPersonOptions()
          .then(setPersons)
          .catch(() => setPersons([]));
      }
    }
  }, [open, form, stepCode, isPeriodic]);

  // ---------- 支部大会双过半实时计算 ----------
  const voteWatch = Form.useWatch('vote', form);
  const quorumInfo = useMemo(() => {
    const should = Number(voteWatch?.shouldAttend ?? 0);
    const actual = Number(voteWatch?.actualAttend ?? 0);
    const agree = Number(voteWatch?.agreeCount ?? 0);
    if (!should) return null;

    // 规则：分母是「应到」人数。乘 2 比较可避免整数除法取整误差。
    const needQuorum = Math.floor(should / 2) + 1;
    const needAgree = Math.floor(should / 2) + 1;
    return {
      needQuorum,
      needAgree,
      quorumOk: actual * 2 > should,
      passOk: agree * 2 > should,
    };
  }, [voteWatch]);

  /** 构造提交体 */
  const buildPayload = (values: Record<string, any>): DevHandleForm => {
    const payload: DevHandleForm = {
      applicantId,
      result: values.result,
      opinion: values.opinion,
      content: values.content,
      advance: isPeriodic ? !!values.advance : true,
      resultType: values.resultType,
      extendMonths: values.extendMonths,
      trainerIds: values.trainerIds,
      introducerIds: values.introducerIds,
    };

    const fmt = (d: unknown) => (d ? dayjs(d as never).format('YYYY-MM-DD') : undefined);

    if (isVote && values.vote) {
      payload.vote = { ...values.vote, meetingDate: fmt(values.vote.meetingDate) };
    }
    if (isTraining && values.training) {
      payload.training = {
        ...values.training,
        startDate: fmt(values.training.startDate),
        endDate: fmt(values.training.endDate),
      };
    }
    if (isTalk && values.talk) {
      payload.talk = { ...values.talk, talkDate: fmt(values.talk.talkDate) };
    }
    if (isPolitical && values.politicalReview) {
      payload.politicalReview = {
        ...values.politicalReview,
        reviewDate: fmt(values.politicalReview.reviewDate),
      };
    }
    return payload;
  };

  /** 提交前预检 */
  const doPreview = async () => {
    try {
      const values = await form.validateFields();
      const res = await previewStep(buildPayload(values));
      setPreview(res);
      if (res.length === 0) message.success('规则预检通过，未发现问题');
    } catch {
      // 校验失败或接口异常，拦截器已提示
    }
  };

  const submit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      const res = await handleStep(buildPayload(values));
      onSuccess(res.resultText, res.warnings);
    } catch {
      // 规则未通过时拦截器已提示
    } finally {
      setSubmitting(false);
    }
  };

  if (!step) return null;

  return (
    <Modal
      open={open}
      title={`办理：${step.stepCode} ${step.stepName}`}
      onCancel={onClose}
      onOk={submit}
      confirmLoading={submitting}
      okText="提交办理"
      cancelText="取消"
      width={760}
      destroyOnHidden
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message={`当事人：${personName ?? '—'}`}
        description={
          step.materialDesc ? `本步骤需归档：${step.materialDesc}` : undefined
        }
      />

      {preview.length > 0 && (
        <Alert
          type={preview.some((p) => p.startsWith('[阻断]')) ? 'error' : 'warning'}
          showIcon
          style={{ marginBottom: 16 }}
          message="规则预检结果"
          description={
            <div>
              {preview.map((p, i) => (
                <div key={i}>{p}</div>
              ))}
            </div>
          }
        />
      )}

      <Form form={form} layout="vertical">
        <Form.Item name="result" label="办理结论" rules={[{ required: true }]}>
          <Radio.Group buttonStyle="solid">
            <Radio.Button value={1}>
              <CheckCircleOutlined /> 通过
            </Radio.Button>
            <Radio.Button value={2}>
              <UndoOutlined /> 驳回（退回上一步）
            </Radio.Button>
            <Radio.Button value={3}>
              <CloseCircleOutlined /> 不通过（终止流程）
            </Radio.Button>
          </Radio.Group>
        </Form.Item>

        {isPeriodic && result === 1 && (
          <>
            <Divider orientation="left" plain>
              考察记录
            </Divider>
            <Form.Item
              name="content"
              label="本次考察情况"
              rules={[{ required: true, message: '请填写本次考察情况' }]}
            >
              <TextArea
                rows={3}
                placeholder="如：参加支部党课 4 次，主动承担社区志愿服务工作，现实表现良好。"
              />
            </Form.Item>
            <Form.Item
              name="advance"
              label="是否推进到下一步骤"
              extra="本步骤为周期性考察。若不勾选，则只记录一次考察，流程停留在本步骤继续考察。"
            >
              <Radio.Group>
                <Radio value={false}>仅记录本次考察</Radio>
                <Radio value={true}>考察期满，推进到下一步</Radio>
              </Radio.Group>
            </Form.Item>
          </>
        )}

        {isTraining && result === 1 && (
          <>
            <Divider orientation="left" plain>
              集中培训（要求不少于 3 天或不少于 24 学时）
            </Divider>
            <Row gutter={12}>
              <Col span={12}>
                <Form.Item name={['training', 'trainingName']} label="培训名称">
                  <Input placeholder="如：2026年第一期为发展对象培训班" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item
                  name={['training', 'organizer']}
                  label="主办单位"
                  extra="基层党委或县级党委组织部门"
                >
                  <Input placeholder="如：郑州市党委" />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item name={['training', 'startDate']} label="开始日期">
                  <DatePicker style={{ width: '100%' }} />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item name={['training', 'endDate']} label="结束日期">
                  <DatePicker style={{ width: '100%' }} />
                </Form.Item>
              </Col>
              <Col span={4}>
                <Form.Item name={['training', 'trainDays']} label="天数">
                  <InputNumber min={0} step={0.5} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
              <Col span={4}>
                <Form.Item name={['training', 'trainHours']} label="学时">
                  <InputNumber min={0} step={0.5} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
            </Row>
          </>
        )}

        {isTalk && result === 1 && (
          <>
            <Divider orientation="left" plain>
              谈话记录
            </Divider>
            <Row gutter={12}>
              <Col span={8}>
                <Form.Item name={['talk', 'talkDate']} label="谈话日期">
                  <DatePicker style={{ width: '100%' }} />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item name={['talk', 'talkerName']} label="谈话人">
                  <Input />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item name={['talk', 'talkerPosition']} label="谈话人职务">
                  <Input placeholder="支部书记 / 组织委员" />
                </Form.Item>
              </Col>
            </Row>
            <Form.Item name={['talk', 'content']} label="谈话内容">
              <TextArea rows={3} />
            </Form.Item>
            <Form.Item name={['talk', 'conclusion']} label="谈话结论">
              <TextArea rows={2} />
            </Form.Item>
          </>
        )}

        {isPolitical && result === 1 && (
          <>
            <Divider orientation="left" plain>
              政治审查
            </Divider>
            <Row gutter={12}>
              <Col span={12}>
                <Form.Item name={['politicalReview', 'reviewDate']} label="审查日期">
                  <DatePicker style={{ width: '100%' }} />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item name={['politicalReview', 'method']} label="审查方法">
                  <Select
                    placeholder="请选择"
                    options={[
                      { label: '同本人谈话', value: '同本人谈话' },
                      { label: '查阅档案资料', value: '查阅档案资料' },
                      { label: '函调', value: '函调' },
                      { label: '外调', value: '外调' },
                    ]}
                  />
                </Form.Item>
              </Col>
            </Row>
            <Form.Item name={['politicalReview', 'attitude']} label="对党的理论和路线、方针、政策的态度">
              <TextArea rows={2} />
            </Form.Item>
            <Form.Item name={['politicalReview', 'history']} label="政治历史和在重大政治斗争中的表现">
              <TextArea rows={2} />
            </Form.Item>
            <Form.Item name={['politicalReview', 'lawAbide']} label="遵纪守法和遵守社会公德情况">
              <TextArea rows={2} />
            </Form.Item>
            <Form.Item name={['politicalReview', 'relatives']} label="直系亲属和主要社会关系的政治情况">
              <TextArea rows={2} />
            </Form.Item>
            <Form.Item name={['politicalReview', 'conclusion']} label="政治审查结论性材料">
              <TextArea rows={3} />
            </Form.Item>
            <Form.Item
              name={['politicalReview', 'reviewResult']}
              label="审查结果"
              extra="未经政治审查或政治审查不合格的，不能发展入党"
            >
              <Radio.Group>
                <Radio value={1}>合格</Radio>
                <Radio value={2}>不合格</Radio>
              </Radio.Group>
            </Form.Item>
          </>
        )}

        {TRAINER_STEPS.includes(stepCode) && result === 1 && (
          <Form.Item
            name="trainerIds"
            label="培养联系人"
            rules={[{ required: true, message: '请指定 1-2 名培养联系人' }]}
            extra="数量：1-2 名正式党员"
          >
            <Select
              mode="multiple"
              placeholder="请选择（1-2 名）"
              showSearch
              optionFilterProp="label"
              maxCount={2}
              options={persons.map((p) => ({ label: p.name, value: p.personId }))}
            />
          </Form.Item>
        )}

        {INTRODUCER_STEPS.includes(stepCode) && result === 1 && (
          <Form.Item
            name="introducerIds"
            label="入党介绍人"
            rules={[{ required: true, message: '请确定 2 名入党介绍人' }]}
            extra="数量：2 名正式党员。受留党察看处分、尚未恢复党员权利的党员，不能作入党介绍人"
          >
            <Select
              mode="multiple"
              placeholder="请选择（2 名）"
              showSearch
              optionFilterProp="label"
              maxCount={2}
              options={persons.map((p) => ({ label: p.name, value: p.personId }))}
            />
          </Form.Item>
        )}

        {isVote && result === 1 && (
          <>
            <Divider orientation="left" plain>
              支部大会表决
            </Divider>

            <Alert
              type="warning"
              showIcon
              style={{ marginBottom: 12 }}
              message="表决规则"
              description="有表决权的到会人数必须超过应到会有表决权人数的半数，才能开会；赞成人数超过应到会有表决权的正式党员的半数，才能通过。两个半数的分母都是「应到」人数。"
            />

            <Row gutter={12}>
              <Col span={8}>
                <Form.Item name={['vote', 'meetingDate']} label="会议日期">
                  <DatePicker style={{ width: '100%' }} />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item name={['vote', 'hostName']} label="主持人">
                  <Input />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item name={['vote', 'recorderName']} label="记录人">
                  <Input />
                </Form.Item>
              </Col>
            </Row>

            <Row gutter={12}>
              <Col span={8}>
                <Form.Item
                  name={['vote', 'shouldAttend']}
                  label="应到会有表决权的正式党员数"
                  rules={[{ required: true, message: '必填' }]}
                >
                  <InputNumber min={1} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item
                  name={['vote', 'actualAttend']}
                  label="实到会有表决权人数"
                  rules={[{ required: true, message: '必填' }]}
                >
                  <InputNumber min={0} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item
                  name={['vote', 'agreeCount']}
                  label="赞成票"
                  rules={[{ required: true, message: '必填' }]}
                >
                  <InputNumber min={0} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item name={['vote', 'opposeCount']} label="反对票">
                  <InputNumber min={0} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item name={['vote', 'abstainCount']} label="弃权票">
                  <InputNumber min={0} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
            </Row>

            {quorumInfo && (
              <Alert
                type={quorumInfo.quorumOk && quorumInfo.passOk ? 'success' : 'error'}
                showIcon
                style={{ marginBottom: 12 }}
                message="实时核算"
                description={
                  <Space direction="vertical" size={2}>
                    <span>
                      开会法定人数：须至少 <b>{quorumInfo.needQuorum}</b> 人到会 ——{' '}
                      {quorumInfo.quorumOk ? (
                        <Tag color="success">已达到</Tag>
                      ) : (
                        <Tag color="error">未达到，不能开会</Tag>
                      )}
                    </span>
                    <span>
                      通过所需赞成票：须超过 <b>{quorumInfo.needAgree}</b> 票 ——{' '}
                      {quorumInfo.passOk ? (
                        <Tag color="success">已过半</Tag>
                      ) : (
                        <Tag color="error">未过半，不能通过</Tag>
                      )}
                    </span>
                  </Space>
                }
              />
            )}

            <Form.Item name={['vote', 'content']} label="会议记录">
              <TextArea rows={3} />
            </Form.Item>
          </>
        )}

        {isBranch && result === 1 && (
          <>
            <Divider orientation="left" plain>
              支部大会讨论结果（三选一）
            </Divider>
            <Form.Item
              name="resultType"
              label="讨论结果"
              rules={[{ required: true, message: '请选择讨论结果' }]}
            >
              <Radio.Group>
                <Radio value={1}>按期转为正式党员</Radio>
                <Radio value={2}>延长预备期</Radio>
                <Radio value={3}>取消预备党员资格</Radio>
              </Radio.Group>
            </Form.Item>

            <Form.Item
              noStyle
              shouldUpdate={(prev, cur) => prev.resultType !== cur.resultType}
            >
              {({ getFieldValue }) =>
                getFieldValue('resultType') === 2 ? (
                  <Form.Item
                    name="extendMonths"
                    label="延长月数"
                    rules={[{ required: true, message: '请填写延长月数' }]}
                    extra="延长不能少于半年（6 个月），最长不超过 1 年（12 个月），且只能延长 1 次"
                  >
                    <InputNumber min={6} max={12} style={{ width: 200 }} addonAfter="个月" />
                  </Form.Item>
                ) : null
              }
            </Form.Item>
          </>
        )}

        <Divider orientation="left" plain>
          办理意见
        </Divider>

        <Form.Item name="opinion" label="意见">
          <TextArea rows={3} placeholder="请填写办理意见" />
        </Form.Item>

        <Form.Item name="materialIds" hidden>
          <Select mode="multiple" />
        </Form.Item>
      </Form>

      <div style={{ textAlign: 'right', marginTop: -8 }}>
        <Text type="secondary" style={{ fontSize: 12, marginRight: 12 }}>
          提交前可先做规则预检
        </Text>
        <a onClick={doPreview}>规则预检</a>
      </div>
    </Modal>
  );
}
