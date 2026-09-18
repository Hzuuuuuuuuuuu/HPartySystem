package com.hparty.develop.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 发展党员 25 步时间轴详情（对应图5 卡片点击后的详情页）。
 */
@Data
public class DevTimelineVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long applicantId;
    private Long personId;
    private String personName;
    private String avatar;
    private Long orgId;
    private String orgName;

    private String currentStage;
    private String currentStageName;
    private String currentStep;
    private String currentStepName;
    private Integer status;
    private String statusLabel;
    private Integer progress;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate applyDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate activistDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate candidateDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate probationaryDate;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate fullMemberDate;

    /** 预备期满日（延长预备期后已顺延） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate probationEndDate;

    /** 已延长预备期次数 */
    private Integer probationExtendCount;

    /** 累计延长月数 */
    private Integer probationExtendMonths;

    /** 支部书记、培养联系人、入党介绍人姓名 */
    private String branchSecretaryName;
    private String trainerNames;
    private String introducerNames;

    /** 五阶段，每阶段下挂步骤节点 */
    private List<StageNode> stages = new ArrayList<>();

    /**
     * 材料齐备度：全流程必备材料总数 / 已上传数。
     *
     * <p>只统计挂在步骤上的必备材料（is_required=1 且非组织台账）；
     * 台账按组织归档、不随个人流程流转，不计入个人齐备度。</p>
     */
    private int requiredMaterialCount;
    private int uploadedMaterialCount;

    /**
     * 阶段节点。
     */
    @Data
    public static class StageNode implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String stageCode;
        private String stageName;
        private Integer stageOrder;
        private String description;

        /** 阶段状态：DONE=已完成 CURRENT=进行中 PENDING=未开始 */
        private String status;

        /** 该阶段已完成步骤数 / 总步骤数 */
        private int doneCount;
        private int totalCount;

        private List<StepNode> steps = new ArrayList<>();

        /** 阶段级个人材料（template.step_code 为空且非组织台账） */
        private List<MaterialTemplateNode> materialTemplates = new ArrayList<>();
    }

    /**
     * 步骤节点。
     */
    @Data
    public static class StepNode implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String stepCode;
        private String stepName;
        private Integer stepOrder;
        private String stageCode;

        /** 步骤类型：1=单次办理 2=周期性考察 */
        private Integer stepType;

        /** 步骤状态：DONE=已办结 CURRENT=待办 PENDING=未开始 TERMINATED=已终止 */
        private String status;

        /** 办理角色说明 */
        private String handleRoles;
        private String handleRolesLabel;

        /** 所需材料说明 */
        private String materialDesc;

        /** 步骤说明（流程图原文） */
        private String description;

        /** 是否分支节点 */
        private Integer isBranch;

        /** 最近一次办理信息 */
        private Long recordId;
        private Integer result;
        private String resultLabel;
        private String opinion;
        private String content;
        private String handleName;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime handleTime;

        /** 应办结时间与超期标志 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime deadlineTime;
        private Boolean overdue;

        /** 本步骤受哪些规则约束 */
        private List<Map<String, String>> rules = new ArrayList<>();

        /** 周期性步骤的历史记录（如每半年的考察记录） */
        private List<RecordNode> history = new ArrayList<>();

        /** 该步骤已归档的材料 */
        private List<Map<String, Object>> materials = new ArrayList<>();

        /** 本步骤需要的材料模板 */
        private List<MaterialTemplateNode> materialTemplates = new ArrayList<>();

        /** 当前登录用户是否可以通过“本人提交”推进该步骤 */
        private Boolean canSelfSubmit;

        /** 本人提交被阻断时的可读原因；非本人办理步骤保持为空 */
        private String selfSubmitBlockedReason;
    }

    /**
     * 步骤所需的一份材料模板（对应 dev_material_template 一行）。
     */
    @Data
    public static class MaterialTemplateNode implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long templateId;

        /** 模板编号，如 1-1、4-1a */
        private String templateCode;

        /** 材料名称 */
        private String templateName;

        /** 材料类型，对应 dev_material.material_type */
        private String materialType;

        /** 是否必备材料：0=否 1=是 */
        private Integer isRequired;

        /** 是否组织台账/名册：1=是（不随个人流程流转） */
        private Integer isRoster;

        /** 提交/出具方编码 */
        private String submitRole;

        /** 提交/出具方中文，如 党支部 */
        private String submitRoleLabel;

        /** 有无空白模板 */
        private Boolean hasBlank;

        /** 有无填写样例 */
        private Boolean hasSample;

        /** 填写说明 */
        private String fillNote;

        /** 该人是否已上传此材料 */
        private Boolean uploaded;

        /** 服务端计算的当前用户上传/替换能力 */
        private Boolean canUpload;

        /** 服务端计算的当前用户删除能力 */
        private Boolean canDelete;

        /** 当前用户是否可预览已归档文件 */
        private Boolean canPreview;

        /** 当前模板有效材料数量 */
        private Integer uploadedCount;

        /** 单份/最新一份材料 ID */
        private Long materialId;

        /** 单份/最新一份材料文件 URL */
        private String fileUrl;

        /** 是否允许同一模板保留多份历史材料 */
        private Boolean repeatable;
    }

    /**
     * 周期性步骤的一条历史记录。
     */
    @Data
    public static class RecordNode implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long recordId;
        private Integer seqNo;
        private Integer result;
        private String resultLabel;
        private String opinion;
        private String content;
        private String handleName;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime handleTime;
    }
}
