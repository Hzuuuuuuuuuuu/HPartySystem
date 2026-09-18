package com.hparty.system.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录成功返回。
 */
@Data
public class LoginVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 令牌值 */
    private String token;

    /** 令牌名称，前端放入请求头时使用 */
    private String tokenName;

    /** 有效期（秒） */
    private long expiresIn;

    /**
     * 是否需要立即修改密码。
     *
     * <p>两种情况会置为 true：从未改过初始密码（{@code pwdUpdateDate} 为空），
     * 或密码已超过有效期。前端应当据此弹出强制改密对话框，
     * 改完之前不应让用户进入业务页面 —— 否则「强制」二字没有意义。</p>
     */
    private boolean needChangePwd;

    /** 提示文案，{@code needChangePwd=true} 时展示 */
    private String changePwdReason;

    /** 密码有效期（天），供前端提示 */
    private Integer passwordExpireDays;
}
