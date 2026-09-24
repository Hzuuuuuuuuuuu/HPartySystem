package com.hparty.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户注册请求（入党申请人自助注册）。
 */
@Data
public class RegisterDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 登录账号（4-20位字母、数字、下划线） */
    @NotBlank(message = "登录账号不能为空")
    @Size(min = 4, max = 20, message = "登录账号长度为4-20个字符")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "登录账号只能包含字母、数字、下划线")
    private String username;

    /** 密码（8-20位，必须包含大小写字母和数字） */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 20, message = "密码长度为8-20个字符")
    private String password;

    /** 确认密码 */
    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;

    /** 真实姓名 */
    @NotBlank(message = "姓名不能为空")
    @Size(min = 2, max = 50, message = "姓名长度为2-50个字符")
    private String name;

    /** 性别（0=女 1=男） */
    private Integer gender;

    /** 身份证号（可选，但如果填写必须合法） */
    @Pattern(regexp = "^$|^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$",
            message = "身份证号格式不正确")
    private String idCard;

    /** 手机号 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 验证码 */
    @NotBlank(message = "验证码不能为空")
    private String code;

    /** 验证码会话标识（取自 /auth/captcha 返回的 uuid） */
    @NotBlank(message = "验证码标识不能为空")
    private String uuid;
}
