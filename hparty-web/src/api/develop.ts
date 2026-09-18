import { http } from './request';
import type { PageQuery, PageResult } from '@/types/api';

// ==================== 类型定义（与后端 VO 对应） ====================

/** 发展党员卡片，对应后端 DevApplicantCardVO */
export interface DevApplicantCard {
  applicantId: number;
  personId: number;
  personName?: string;
  avatar?: string;
  sex?: number;
  orgId?: number;
  orgName?: string;
  currentStage?: string;
  currentStageName?: string;
  currentStep?: string;
  currentStepName?: string;
  status?: number;
  statusLabel?: string;
  progress?: number;
  memberStatus?: number;
  memberStatusLabel?: string;
  applyDate?: string;
  activistDate?: string;
  candidateDate?: string;
  probationaryDate?: string;
  deadlineTime?: string;
  overdue?: boolean;
}

export interface DevApplicantQuery extends PageQuery {
  keyword?: string;
  orgId?: number;
  currentStage?: string;
  currentStep?: string;
  status?: number;
  memberStatus?: number;
}

/** 阶段模板 */
export interface DevStage {
  stageId: number;
  stageCode: string;
  stageName: string;
  stageOrder: number;
  description?: string;
}

/** 步骤模板 */
export interface DevStep {
  stepId: number;
  stepCode: string;
  stepName: string;
  stageCode: string;
  stepOrder: number;
  /** 1=单次办理 2=周期性考察 */
  stepType: number;
  handleRoles?: string;
  handleOrgType?: number;
  needVote?: number;
  deadlineDays?: number;
  intervalDays?: number;
  intervalBaseStep?: string;
  periodicDays?: number;
  /** 最少培训天数（如集中培训） */
  minTrainingDays?: number;
  /** 最少培训学时 */
  minTrainingHours?: number;
  ruleKey?: string;
  materialDesc?: string;
  description?: string;
  isBranch?: number;
}

/** 材料模板（dev_material_template），对应后端 DevMaterialTemplate */
export interface DevMaterialTemplate {
  templateId: number;
  /** 模板编号，如 1-1、4-1a */
  templateCode: string;
  templateName: string;
  /** STAGE_1..STAGE_5 */
  stageCode: string;
  /** 关联步骤；null 表示阶段通用或全程通用 */
  stepCode?: string;
  materialType?: string;
  /** 1=必备 0=选填 */
  isRequired: number;
  /** 1=组织台账/名册 */
  isRoster: number;
  submitRole?: string;
  blankFile?: string;
  sampleFile?: string;
  fillNote?: string;
  orderNum: number;
  remark?: string;
}

/** 时间轴步骤里的材料模板节点，对应后端 DevTimelineVO.MaterialTemplateNode */
export interface DevMaterialTemplateNode {
  templateId: number;
  templateCode: string;
  templateName: string;
  materialType?: string;
  isRequired?: number;
  isRoster?: number;
  submitRole?: string;
  submitRoleLabel?: string;
  hasBlank?: boolean;
  hasSample?: boolean;
  fillNote?: string;
  /** 该人是否已上传此材料 */
  uploaded?: boolean;
  /** 服务端计算的材料能力；前端不要按角色自行推断 */
  canUpload?: boolean;
  canDelete?: boolean;
  canPreview?: boolean;
  uploadedCount?: number;
  materialId?: number;
  fileUrl?: string;
  repeatable?: boolean;
}

/** 25 步时间轴步骤节点 */
export interface DevStepNode {
  stepCode: string;
  stepName: string;
  stepOrder: number;
  stageCode: string;
  stepType?: number;
  /** DONE / CURRENT / PENDING / TERMINATED */
  status: string;
  handleRoles?: string;
  handleRolesLabel?: string;
  materialDesc?: string;
  description?: string;
  isBranch?: number;
  recordId?: number;
  result?: number;
  resultLabel?: string;
  opinion?: string;
  content?: string;
  handleName?: string;
  handleTime?: string;
  deadlineTime?: string;
  overdue?: boolean;
  rules?: { ruleKey: string; description: string }[];
  history?: {
    recordId: number;
    seqNo?: number;
    result?: number;
    resultLabel?: string;
    opinion?: string;
    content?: string;
    handleName?: string;
    handleTime?: string;
  }[];
  materials?: {
    materialId: number;
    materialType?: string;
    materialName: string;
    fileUrl?: string;
    submitDate?: string;
  }[];
  /** 本步骤需要的材料模板 */
  materialTemplates?: DevMaterialTemplateNode[];
  /** 服务端计算的本人提交能力 */
  canSelfSubmit?: boolean;
  selfSubmitBlockedReason?: string;
}

/** 阶段节点 */
export interface DevStageNode {
  stageCode: string;
  stageName: string;
  stageOrder: number;
  description?: string;
  status: string;
  doneCount: number;
  totalCount: number;
  steps: DevStepNode[];
  /** 非 roster 的阶段级个人材料 */
  materialTemplates?: DevMaterialTemplateNode[];
}

/** 25 步时间轴详情 */
export interface DevTimeline {
  applicantId: number;
  personId: number;
  personName?: string;
  avatar?: string;
  orgId?: number;
  orgName?: string;
  currentStage?: string;
  currentStageName?: string;
  currentStep?: string;
  currentStepName?: string;
  status?: number;
  statusLabel?: string;
  progress?: number;
  applyDate?: string;
  activistDate?: string;
  candidateDate?: string;
  probationaryDate?: string;
  fullMemberDate?: string;
  probationExtensionCount?: number;
  probationExtendCount?: number;
  branchSecretaryName?: string;
  trainerNames?: string;
  introducerNames?: string;
  /** 材料齐备度：必备材料总数 / 已上传数 */
  requiredMaterialCount?: number;
  uploadedMaterialCount?: number;
  stages: DevStageNode[];
}

/** 办理表单，对应后端 DevHandleDTO */
export interface DevHandleForm {
  applicantId: number;
  /** 1=通过 2=驳回 3=不通过 */
  result: number;
  opinion?: string;
  content?: string;
  materialIds?: number[];
  /** 周期性考察步骤：是否推进到下一步 */
  advance?: boolean;
  vote?: {
    meetingDate?: string;
    meetingPlace?: string;
    shouldAttend: number;
    actualAttend: number;
    agreeCount: number;
    opposeCount?: number;
    abstainCount?: number;
    hostName?: string;
    recorderName?: string;
    content?: string;
  };
  training?: {
    trainingName?: string;
    organizer?: string;
    startDate?: string;
    endDate?: string;
    trainDays?: number;
    trainHours?: number;
    isQualified?: number;
    remark?: string;
  };
  talk?: {
    talkDate?: string;
    talkPlace?: string;
    talkerName?: string;
    talkerPosition?: string;
    content?: string;
    conclusion?: string;
  };
  politicalReview?: {
    reviewDate?: string;
    attitude?: string;
    history?: string;
    lawAbide?: string;
    relatives?: string;
    method?: string;
    conclusion?: string;
    reviewResult?: number;
  };
  trainerIds?: number[];
  introducerIds?: number[];
  /** STEP_23 分支：1=按期转正 2=延长预备期 3=取消资格 */
  resultType?: number;
  extendMonths?: number;
}

/** 办理结果 */
export interface DevHandleResult {
  applicantId: number;
  currentStage?: string;
  currentStageName?: string;
  currentStep?: string;
  currentStepName?: string;
  status?: number;
  statusLabel?: string;
  progress?: number;
  resultText?: string;
  finished?: boolean;
  warnings?: string[];
}

// ==================== 接口 ====================

/** 卡片墙分页 */
export const pageApplicants = (params: DevApplicantQuery) =>
  http.get<PageResult<DevApplicantCard>>('/develop/applicant/page', params);

/** 25 步时间轴详情 */
export const getTimeline = (applicantId: number) =>
  http.get<DevTimeline>(`/develop/applicant/${applicantId}/timeline`);

/** 上传/替换一份个人发展材料；归属字段全部由后端派生 */
export const uploadApplicantMaterial = (applicantId: number, templateId: number, file: File) => {
  const form = new FormData();
  form.append('file', file);
  return http.upload<number>(`/develop/applicant/${applicantId}/materials/${templateId}`, form);
};

/** 删除一份当前用户有权管理的个人发展材料 */
export const deleteApplicantMaterial = (applicantId: number, materialId: number) =>
  http.delete<void>(`/develop/applicant/${applicantId}/materials/${materialId}`);

/** 申请人本人提交当前 APPLICANT 办理步骤 */
export const selfSubmitApplicantStep = (applicantId: number) =>
  http.post<DevHandleResult>(`/develop/applicant/${applicantId}/self-submit`);

/** 阶段人数统计 */
export const getStatistics = () =>
  http.get<{ total: number; stages: { stageCode: string; stageName: string; count: number }[] }>(
    '/develop/applicant/statistics',
  );

/** 新增发展对象 */
export const addApplicant = (data: Record<string, unknown>) =>
  http.post<number>('/develop/applicant', data);

/** 修改发展对象 */
export const updateApplicant = (data: Record<string, unknown>) =>
  http.put<void>('/develop/applicant', data);

/** 删除发展对象 */
export const removeApplicant = (applicantId: number) =>
  http.delete<void>(`/develop/applicant/${applicantId}`);

/** 办理步骤 */
export const handleStep = (data: DevHandleForm) =>
  http.post<DevHandleResult>('/develop/flow/handle', data);

/** 提交前规则预检 */
export const previewStep = (data: DevHandleForm) =>
  http.post<string[]>('/develop/flow/preview', data);

/** 阶段模板 */
export const listStages = () => http.get<DevStage[]>('/develop/flow/stages');

/** 步骤模板 */
export const listSteps = () => http.get<DevStep[]>('/develop/flow/steps');

/** 某步骤的规则说明 */
export const listStepRules = (stepCode: string) =>
  http.get<{ ruleKey: string; description: string }[]>(`/develop/flow/steps/${stepCode}/rules`);

// ==================== 材料模板 ====================

/** 材料模板列表（可按阶段/步骤过滤） */
export const listMaterialTemplates = (params?: { stageCode?: string; stepCode?: string }) =>
  http.get<DevMaterialTemplate[]>('/develop/material/template/list', params);

/** 某步骤需要的材料模板 */
export const listStepMaterialTemplates = (stepCode: string) =>
  http.get<DevMaterialTemplate[]>(`/develop/material/template/step/${stepCode}`);

/** 材料模板下载地址（type=blank 空白模板 / type=sample 填写样例） */
export const materialTemplateDownloadUrl = (templateId: number, type: 'blank' | 'sample') =>
  `/develop/material/template/${templateId}/download?type=${type}`;

/**
 * 下载材料模板。
 *
 * <p>走 axios blob 通道（带上 Authorization 头），再从 Content-Disposition 里
 * 解析 RFC 5987 的 filename*，最后用临时 a 标签触发保存。</p>
 */
export async function downloadMaterialTemplate(
  templateId: number,
  type: 'blank' | 'sample',
  fallbackName: string,
) {
  const res = await http.download(materialTemplateDownloadUrl(templateId, type));

  // 后端抛 BizException 时返回的是 JSON（blob 通道下拦截器不解析 code），这里还原成错误信息
  if (res.data?.type?.includes('application/json')) {
    let msg = '模板下载失败';
    try {
      msg = (JSON.parse(await res.data.text()) as { msg?: string }).msg || msg;
    } catch {
      /* 拿不到就用默认文案 */
    }
    throw new Error(msg);
  }

  const disposition = (res.headers?.['content-disposition'] as string | undefined) ?? '';

  let fileName = fallbackName;
  const star = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
  if (star?.[1]) {
    try {
      fileName = decodeURIComponent(star[1]);
    } catch {
      /* 解析失败就用兜底名 */
    }
  }

  const url = URL.createObjectURL(res.data);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

/** 阶段配色，用于标签与进度条 */
export const STAGE_COLORS: Record<string, string> = {
  STAGE_1: '#8C8C8C',
  STAGE_2: '#FAAD14',
  STAGE_3: '#1890FF',
  STAGE_4: '#722ED1',
  STAGE_5: '#C7000B',
};

/** 阶段简称，用于卡片角标 */
export const STAGE_SHORT: Record<string, string> = {
  STAGE_1: '第一阶段',
  STAGE_2: '第二阶段',
  STAGE_3: '第三阶段',
  STAGE_4: '第四阶段',
  STAGE_5: '第五阶段',
};
