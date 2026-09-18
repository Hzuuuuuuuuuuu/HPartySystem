package com.hparty.system.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hparty.common.core.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 登录日志 / 操作日志查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysLogQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 操作人 / 登录账号 */
    private String username;

    /** 操作模块（仅操作日志） */
    private String title;

    /** 业务类型（仅操作日志）：0=其它 1=新增 2=修改 3=删除 4=审批 5=导出 6=上传 */
    private Integer businessType;

    /** 状态：0=失败 1=成功 */
    private Integer status;

    /** 起始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime beginTime;

    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
