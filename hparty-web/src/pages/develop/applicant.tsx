import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Card,
  Empty,
  Pagination,
  Segmented,
  Space,
  Spin,
  Tag,
  Tooltip,
  App as AntdApp,
  Modal,
  Form,
  Select,
  DatePicker,
  Input,
} from 'antd';
import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  WarningFilled,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  addApplicant,
  listStages,
  pageApplicants,
  removeApplicant,
  updateApplicant,
  STAGE_COLORS,
  STAGE_SHORT,
  type DevApplicantCard,
  type DevStage,
} from '@/api/develop';
import { listPersonOptions, type PersonOption } from '@/api/system';
import { useUserStore } from '@/store/user';

const PAGE_SIZE = 12;

export default function DevelopApplicantPage() {
  const navigate = useNavigate();
  const { message, modal } = AntdApp.useApp();
  const can = useUserStore((s) => s.can);
  const canViewDetail = can('develop:applicant:detail');

  const [loading, setLoading] = useState(false);
  const [cards, setCards] = useState<DevApplicantCard[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNum, setPageNum] = useState(1);
  const [stageFilter, setStageFilter] = useState<string>('');

  const [stages, setStages] = useState<DevStage[]>([]);
  const [selected, setSelected] = useState<DevApplicantCard | null>(null);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<DevApplicantCard | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [persons, setPersons] = useState<PersonOption[]>([]);
  const [form] = Form.useForm();

  /** 拉取列表 */
  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      const res = await pageApplicants({
        pageNum,
        pageSize: PAGE_SIZE,
        currentStage: stageFilter || undefined,
      });
      setCards(res.records ?? []);
      setTotal(res.total ?? 0);
    } catch {
      setCards([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [pageNum, stageFilter]);

  useEffect(() => {
    fetchList();
  }, [fetchList]);

  useEffect(() => {
    listStages()
      .then(setStages)
      .catch(() => setStages([]));
  }, []);

  /** 阶段筛选项：全部 + 5 个阶段 */
  const segmentedOptions = useMemo(
    () => [
      { label: '全部', value: '' },
      ...stages.map((s) => ({
        label: STAGE_SHORT[s.stageCode] ?? s.stageName,
        value: s.stageCode,
      })),
    ],
    [stages],
  );

  /** 打开发起流程弹窗 */
  const openAdd = async () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
    try {
      setPersons(await listPersonOptions());
    } catch {
      setPersons([]);
    }
  };

  /** 打开编辑弹窗 */
  const openEdit = (card: DevApplicantCard) => {
    setEditing(card);
    form.setFieldsValue({
      personId: card.personId,
      applyDate: card.applyDate ? dayjs(card.applyDate) : undefined,
    });
    setModalOpen(true);
  };

  const submitForm = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      const payload = {
        applicantId: editing?.applicantId,
        personId: values.personId,
        applyDate: values.applyDate ? values.applyDate.format('YYYY-MM-DD') : undefined,
        remark: values.remark,
      };
      if (editing) {
        await updateApplicant(payload);
        message.success('修改成功');
      } else {
        await addApplicant(payload);
        message.success('已发起发展党员流程');
      }
      setModalOpen(false);
      fetchList();
    } catch {
      // 错误提示由拦截器统一处理
    } finally {
      setSubmitting(false);
    }
  };

  const confirmRemove = () => {
    if (!selected) return;
    modal.confirm({
      title: `确认删除「${selected.personName}」的发展党员流程？`,
      content: '删除后流程记录将不再展示，历史轨迹保留在数据库中。',
      okText: '确认删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await removeApplicant(selected.applicantId);
        message.success('删除成功');
        setSelected(null);
        fetchList();
      },
    });
  };

  return (
    <div>
      <Card variant="borderless" style={{ marginBottom: 12 }} styles={{ body: { padding: 16 } }}>
        <Space wrap>
          {can('develop:applicant:add') && (
            <Button type="primary" icon={<PlusOutlined />} onClick={openAdd}>
              添加发展对象
            </Button>
          )}
          {can('develop:applicant:edit') && (
            <Button icon={<EditOutlined />} disabled={!selected} onClick={() => selected && openEdit(selected)}>
              编辑
            </Button>
          )}
          {can('develop:applicant:remove') && (
            <Button icon={<DeleteOutlined />} disabled={!selected} onClick={confirmRemove}>
              删除
            </Button>
          )}
          <Button icon={<ReloadOutlined />} onClick={fetchList}>
            刷新
          </Button>
        </Space>
      </Card>

      <Card variant="borderless" styles={{ body: { padding: 20 } }}>
        <Segmented
          options={segmentedOptions}
          value={stageFilter}
          onChange={(v) => {
            setStageFilter(String(v));
            setPageNum(1);
            setSelected(null);
          }}
          style={{ marginBottom: 20 }}
        />

        <Spin spinning={loading}>
          {cards.length === 0 && !loading ? (
            <Empty description="暂无发展党员数据" style={{ padding: '60px 0' }} />
          ) : (
            <div className="person-grid">
              {cards.map((card) => (
                <PersonCard
                  key={card.applicantId}
                  card={card}
                  active={selected?.applicantId === card.applicantId}
                  canOpen={canViewDetail}
                  onSelect={() => setSelected(card)}
                  onOpen={() => navigate(`/develop/applicant/${card.applicantId}`)}
                />
              ))}
            </div>
          )}
        </Spin>

        {total > 0 && (
          <div style={{ textAlign: 'right', marginTop: 24 }}>
            <Pagination
              current={pageNum}
              pageSize={PAGE_SIZE}
              total={total}
              showSizeChanger={false}
              showTotal={(t) => `共 ${t} 条`}
              onChange={(p) => {
                setPageNum(p);
                setSelected(null);
              }}
            />
          </div>
        )}
      </Card>

      <Modal
        open={modalOpen}
        title={editing ? '编辑发展对象' : '添加发展对象'}
        onCancel={() => setModalOpen(false)}
        onOk={submitForm}
        confirmLoading={submitting}
        okText="确定"
        cancelText="取消"
        destroyOnHidden
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="personId"
            label="人员"
            rules={[{ required: true, message: '请选择人员' }]}
          >
            <Select
              placeholder="请选择人员"
              showSearch
              optionFilterProp="label"
              disabled={!!editing}
              options={persons.map((p) => ({
                label: `${p.name}${p.orgName ? `（${p.orgName}）` : ''}`,
                value: p.personId,
              }))}
            />
          </Form.Item>

          <Form.Item name="applyDate" label="递交入党申请书日期">
            <DatePicker style={{ width: '100%' }} placeholder="选择日期" />
          </Form.Item>

          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={3} placeholder="选填" maxLength={200} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

interface PersonCardProps {
  card: DevApplicantCard;
  active: boolean;
  canOpen: boolean;
  onSelect: () => void;
  onOpen: () => void;
}

function PersonCard({ card, active, canOpen, onSelect, onOpen }: PersonCardProps) {
  const stageColor = STAGE_COLORS[card.currentStage ?? ''] ?? '#8C8C8C';

  return (
    <div
      className="person-card"
      onClick={onSelect}
      onDoubleClick={canOpen ? onOpen : undefined}
      style={active ? { borderColor: '#C7000B', boxShadow: '0 0 0 2px rgba(199,0,11,.16)' } : undefined}
    >
      <div className="person-card__photo">
        {card.avatar ? <img src={card.avatar} alt={card.personName} /> : (card.personName ?? '—').slice(0, 1)}
      </div>

      <div className="person-card__body">
        <div className="person-card__name">{card.personName ?? '未知'}</div>
        <div className="person-card__org">{card.orgName ?? '—'}</div>

        <Tag color={stageColor} style={{ marginInlineEnd: 0, borderRadius: 10, paddingInline: 10 }}>
          {STAGE_SHORT[card.currentStage ?? ''] ?? card.currentStageName ?? '—'}
        </Tag>

        <div style={{ marginTop: 8, fontSize: 12, color: '#8C8C8C' }}>
          {card.overdue ? (
            <Tooltip title={`应于 ${card.deadlineTime} 前办结`}>
              <span style={{ color: '#FF4D4F' }}>
                <WarningFilled /> 已超期
              </span>
            </Tooltip>
          ) : (
            <span>当前：{card.currentStepName ?? '—'}</span>
          )}
        </div>

        {canOpen && (
          <Button
            type="link"
            size="small"
            style={{ marginTop: 4, padding: 0 }}
            onClick={(e) => {
              e.stopPropagation();
              onOpen();
            }}
          >
            查看 25 步详情
          </Button>
        )}
      </div>
    </div>
  );
}
