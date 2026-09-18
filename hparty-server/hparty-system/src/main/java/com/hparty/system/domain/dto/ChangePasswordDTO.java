package com.hparty.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户自助修改密码。
 *
 * <p>与管理员「重置密码」不同 —— 自助改密必须验证原密码，
 * 否则会话被劫持后攻击者可直接改掉密码把账号锁死。</p>
 */
@Data
public class ChangePasswordDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 原密码 */
    @NotBlank(message = "请输入原密码")
    private String oldPassword;

    /** 新密码 */
    @NotBlank(message = "请输入新密码")
    private String newPassword;
}
