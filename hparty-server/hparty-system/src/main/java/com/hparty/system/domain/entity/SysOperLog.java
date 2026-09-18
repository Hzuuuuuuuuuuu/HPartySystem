package com.hparty.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志
 */
@Data
@TableName("sys_oper_log")
public class SysOperLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 日志主键 */
    @TableId(value = "oper_id", type = IdType.AUTO)
    private Long operId;

    /** 模块标题 */
    private String title;

    /** 业务类型：0=其它 1=新增 2=修改 3=删除 4=审批 5=导出 6=上传 */
    private Integer businessType;

    /** 方法名称 */
    private String method;

    /** 请求方式 */
    private String requestMethod;

    /** 操作人员 */
    private String operName;

    /** 请求URL */
    private String operUrl;

    /** 主机地址 */
    private String operIp;

    /** 请求参数 */
    private String operParam;

    /** 返回参数 */
    private String jsonResult;

    /** 状态：0=异常 1=正常 */
    private Integer status;

    /** 错误消息 */
    private String errorMsg;

    /** 消耗时间(毫秒) */
    private Long costTime;

    /** 操作时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime operTime;
}
