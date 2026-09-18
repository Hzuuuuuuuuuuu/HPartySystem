package com.hparty.system.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 单条待办。
 *
 * <p>字段与 {@code docs/07-待开发需求.md} P0-3 给出的 JSON 示例保持一致：
 * {@code key / title / description / deadline / overdue / link}。</p>
 */
@Data
public class TodoItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 稳定标识，形如 {@code dev-2-STEP_02}，供前端做 key 与去重 */
    private String key;

    /** 标题，如「冯刚 · STEP_02 党组织派人谈话」 */
    private String title;

    /** 副标题，如「第二支部 · 已超期 16 天」 */
    private String description;

    /** 应办结时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deadline;

    /** 是否已超期 */
    private Boolean overdue;

    /** 超期天数，未超期为 0 */
    private Long overdueDays;

    /** 点击跳转的前端路由 */
    private String link;
}
