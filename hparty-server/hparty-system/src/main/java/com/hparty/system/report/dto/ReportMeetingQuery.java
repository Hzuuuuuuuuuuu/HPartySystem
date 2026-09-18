package com.hparty.system.report.dto;

import com.hparty.common.core.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDate;

/**
 * 三会一课开展情况导出条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReportMeetingQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主办党组织 */
    private Long orgId;

    /** 会议类型：MEMBER_ASSEMBLY / BRANCH_COMMITTEE / PARTY_GROUP / PARTY_LECTURE / THEME_PARTY_DAY / ORG_LIFE */
    private String meetingType;

    /** 状态：0=草稿 1=待召开 2=进行中 3=已结束 4=已归档 */
    private Integer status;

    /** 标题关键字 */
    private String keyword;

    /** 会议日期起（含） */
    private LocalDate startDate;

    /** 会议日期止（含） */
    private LocalDate endDate;
}
