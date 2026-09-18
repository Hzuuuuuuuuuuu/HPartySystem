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
 * 登录日志
 */
@Data
@TableName("sys_login_log")
public class SysLoginLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 访问ID */
    @TableId(value = "info_id", type = IdType.AUTO)
    private Long infoId;

    /** 用户账号 */
    private String username;

    /** 登录IP */
    private String ipaddr;

    /** 登录地点 */
    private String loginLocation;

    /** 浏览器 */
    private String browser;

    /** 操作系统 */
    private String os;

    /** 状态：0=失败 1=成功 */
    private Integer status;

    /** 提示消息 */
    private String msg;

    /** 登录时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime loginTime;
}
