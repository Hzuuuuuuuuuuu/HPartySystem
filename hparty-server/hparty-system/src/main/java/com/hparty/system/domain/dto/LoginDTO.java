package com.hparty.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录请求。
 */
@Data
public class LoginDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 登录账号 */
    @NotBlank(message = "登录账号不能为空")
    private String username;

    /** 密码（明文传输，由 HTTPS 保障；服务端不做可逆存储） */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 验证码 */
    private String code;

    /** 验证码会话标识（取自 /auth/captcha 返回的 uuid） */
    private String uuid;
}
