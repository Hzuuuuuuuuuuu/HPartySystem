package com.hparty.system.domain.dto;

import com.hparty.common.core.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 系统用户查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "系统用户查询条件")
public class SysUserQuery extends PageQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 登录账号，模糊匹配 */
    @Schema(description = "登录账号，模糊匹配")
    private String username;

    /** 昵称，模糊匹配 */
    @Schema(description = "昵称，模糊匹配")
    private String nickName;

    /** 手机号，模糊匹配 */
    @Schema(description = "手机号，模糊匹配")
    private String phone;

    /** 所属党组织 */
    @Schema(description = "所属党组织 ID")
    private Long orgId;

    /** 状态：0=停用 1=正常 */
    @Schema(description = "状态：0=停用 1=正常")
    private Integer status;
}
