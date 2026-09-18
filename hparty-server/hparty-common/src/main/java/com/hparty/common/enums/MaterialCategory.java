package com.hparty.common.enums;

import lombok.Getter;

/**
 * 会议/组织生活会材料分类，对应图2 的九宫格。
 */
@Getter
public enum MaterialCategory {

    NOTICE("NOTICE", "通知", "BellOutlined"),
    PRE_STUDY("PRE_STUDY", "会前学习", "BookOutlined"),
    RECORD("RECORD", "记录", "EditOutlined"),
    ANALYSIS("ANALYSIS", "党员剖析材料", "FileTextOutlined"),
    SELF_EVAL("SELF_EVAL", "党员自评材料", "FileDoneOutlined"),
    OTHER("OTHER", "其它内容", "AppstoreOutlined"),
    PROBLEM_LIST("PROBLEM_LIST", "问题清单", "QuestionCircleOutlined"),
    RECTIFY_LIST("RECTIFY_LIST", "整改清单", "CalendarOutlined"),
    MEETING_MINUTES("MEETING_MINUTES", "会议记录", "FileWordOutlined"),
    DEMOCRATIC_EVAL("DEMOCRATIC_EVAL", "民主评议党员", "AuditOutlined"),
    SITUATION_REPORT("SITUATION_REPORT", "情况报告", "LineChartOutlined");

    private final String code;
    private final String label;
    private final String icon;

    MaterialCategory(String code, String label, String icon) {
        this.code = code;
        this.label = label;
        this.icon = icon;
    }

    public static String labelOf(String code) {
        for (MaterialCategory c : values()) {
            if (c.code.equals(code)) {
                return c.label;
            }
        }
        return "";
    }
}
