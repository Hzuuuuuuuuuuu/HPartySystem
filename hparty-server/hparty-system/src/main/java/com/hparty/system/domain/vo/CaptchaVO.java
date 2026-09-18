package com.hparty.system.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 验证码。
 */
@Data
@AllArgsConstructor
public class CaptchaVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 验证码会话标识，登录时原样回传 */
    private String uuid;

    /** 验证码图片，Base64 data URI，可直接用于 img 的 src */
    private String img;

    /** 验证码是否开启。关闭时前端应隐藏输入框 */
    private boolean enabled;
}
