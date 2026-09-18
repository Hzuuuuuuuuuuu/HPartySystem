package com.hparty.system.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 系统用户新增 / 修改入参。
 * <p>新增时 {@code password} 必填；修改时忽略该字段，请使用重置密码接口。</p>
 */
@Data
@Schema(description = "系统用户新增/修改入参")
public class SysUserDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID，新增时为空 */
    @Schema(description = "用户 ID，新增时为空")
    private Long userId;

    /** 登录账号 */
    @Schema(description = "登录账号")
    private String username;

    /** 明文密码，仅新增时使用，服务端以 BCrypt 加密存储 */
    @Schema(description = "明文密码，仅新增时使用")
    private String password;

    /** 昵称 */
    @Schema(description = "昵称")
    private String nickName;

    /** 关联人员档案 ID（party_person.person_id） */
    @Schema(description = "关联人员档案 ID")
    private Long personId;

    /** 所属党组织 ID */
    @Schema(description = "所属党组织 ID")
    private Long orgId;

    /** 手机号 */
    @Schema(description = "手机号")
    private String phone;

    /** 邮箱 */
    @Schema(description = "邮箱")
    private String email;

    /** 头像 URL */
    @Schema(description = "头像 URL")
    private String avatar;

    /** 性别：0=未知 1=男 2=女 */
    @Schema(description = "性别：0=未知 1=男 2=女")
    private Integer sex;

    /** 状态：0=停用 1=正常 */
    @Schema(description = "状态：0=停用 1=正常")
    private Integer status;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;

    /** 角色 ID 集合，全量覆盖 */
    @Schema(description = "角色 ID 集合，全量覆盖")
    private List<Long> roleIds;
}
