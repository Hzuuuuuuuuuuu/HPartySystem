package com.hparty.develop.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 办理发展党员步骤的提交表单。
 *
 * <p>25 个步骤共用一个表单对象，按步骤类型取用不同字段：
 * 表决类步骤用 {@link #vote}，培训步骤用 {@link #training}，
 * 谈话类步骤用 {@link #talk}，政治审查用 {@link #politicalReview}。</p>
 */
@Data
public class DevHandleDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 申请人实例 ID */
    @NotNull(message = "申请人实例ID不能为空")
    private Long applicantId;

    /**
     * 办理结论，对应 dev_step_record.result：
     * 1=通过 2=驳回(退回上一步) 3=不通过(终止)
     */
    @NotNull(message = "办理结论不能为空")
    private Integer result;

    /** 办理意见 */
    private String opinion;

    /** 考察记录内容（周期性步骤 STEP_06 / STEP_21 使用） */
    private String content;

    /**
     * 是否推进到下一步骤。
     *
     * <p><b>为什么需要这个字段</b>：周期性考察步骤（STEP_06 培养教育考察、
     * STEP_21 继续教育考察）同时受两条约束 ——
     * 「每半年记录一次」和「须满 1 年方可推进」。
     * 若把后者的期限校验套在每一次记录上，那么前几次考察记录都会被
     * 「未满 365 天」挡住，半年一次的考察永远记不成。</p>
     *
     * <p>因此区分两种动作：</p>
     * <ul>
     *   <li>{@code advance=false}（默认）—— 只是记录一次考察，停留在本步骤，
     *       只校验记录频率（{@code PERIODIC_RULE}）</li>
     *   <li>{@code advance=true} —— 考察期满，推进到下一步骤，
     *       此时才校验期限（{@code INTERVAL_RULE}）</li>
     * </ul>
     *
     * <p>对单次办理型步骤（其余 23 步）此字段无意义，通过即推进。</p>
     */
    private Boolean advance;

    /** 关联材料 ID 列表 */
    private List<Long> materialIds;

    // ==================== 步骤专属数据 ====================

    /** 表决数据（STEP_15 / STEP_23） */
    private VoteDTO vote;

    /** 集中培训数据（STEP_11） */
    private TrainingDTO training;

    /** 谈话记录（STEP_02 / STEP_16） */
    private TalkDTO talk;

    /** 政治审查（STEP_10） */
    private PoliticalReviewDTO politicalReview;

    /** 培养联系人 ID（STEP_05，1-2 名正式党员） */
    private List<Long> trainerIds;

    /** 入党介绍人 ID（STEP_09，2 名正式党员） */
    private List<Long> introducerIds;

    // ==================== STEP_23 分支出口 ====================

    /**
     * 转正讨论结果类型：1=按期转正 2=延长预备期 3=取消预备党员资格
     * <p>仅当 result=1（通过）时才有意义。</p>
     */
    private Integer resultType;

    /** 延长预备期的月数，须 >=6 且 <=12，且全流程仅允许延长 1 次 */
    private Integer extendMonths;

    // ==================== 嵌套表单 ====================

    /**
     * 支部大会表决数据。
     * <p>党务规则对两个「半数」有明确要求，字段名与流程图术语保持一致，
     * 便于经办人对照填写。</p>
     */
    @Data
    public static class VoteDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate meetingDate;

        private String meetingPlace;

        /** 应到会有表决权的正式党员数 */
        @NotNull(message = "应到会有表决权的正式党员数不能为空")
        private Integer shouldAttend;

        /** 实到会有表决权人数 */
        @NotNull(message = "实到会有表决权人数不能为空")
        private Integer actualAttend;

        /** 赞成票 */
        @NotNull(message = "赞成票数不能为空")
        private Integer agreeCount;

        /** 反对票 */
        private Integer opposeCount;

        /** 弃权票 */
        private Integer abstainCount;

        /** 主持人姓名 */
        private String hostName;

        /** 记录人姓名 */
        private String recorderName;

        /** 会议记录 */
        private String content;
    }

    /** 集中培训数据 */
    @Data
    public static class TrainingDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String trainingName;

        /** 主办单位：基层党委 / 县级党委组织部门 */
        private String organizer;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate startDate;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate endDate;

        /** 培训天数，须 >=3 */
        private BigDecimal trainDays;

        /** 培训学时，须 >=24 */
        private BigDecimal trainHours;

        /** 是否合格 */
        private Integer isQualified;

        private String remark;
    }

    /** 谈话记录 */
    @Data
    public static class TalkDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate talkDate;

        private String talkPlace;

        /** 谈话人姓名 */
        private String talkerName;

        /** 谈话人职务 */
        private String talkerPosition;

        /** 谈话内容 */
        private String content;

        /** 谈话结论 / 对能否入党的意见 */
        private String conclusion;
    }

    /** 政治审查 */
    @Data
    public static class PoliticalReviewDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate reviewDate;

        /** 对党的理论和路线、方针、政策的态度 */
        private String attitude;

        /** 政治历史和在重大政治斗争中的表现 */
        private String history;

        /** 遵纪守法和遵守社会公德情况 */
        private String lawAbide;

        /** 直系亲属和主要社会关系的政治情况 */
        private String relatives;

        /** 审查方法：同本人谈话 / 查阅档案 / 函调 / 外调 */
        private String method;

        /** 结论性材料 */
        private String conclusion;

        /** 审查结果：1=合格 2=不合格 */
        private Integer reviewResult;
    }
}
