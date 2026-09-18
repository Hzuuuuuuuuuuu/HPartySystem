package com.hparty.system.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统用户视图对象，不含密码字段。
 */
@Data
@Schema(description = "系统用户")
public class SysUserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    @Schema(description = "用户 ID")
    private Long userId;

    /** 登录账号 */
    @Schema(description = "登录账号")
    private String username;

    /** 昵称 */
    @Schema(description = "昵称")
    private String nickName;

    /** 关联人员档案 ID */
    @Schema(description = "关联人员档案 ID")
    private Long personId;

    /** 所属党组织 ID */
    @Schema(description = "所属党组织 ID")
    private Long orgId;

    /** 所属党组织名称 */
    @Schema(description = "所属党组织名称")
    private String orgName;

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

    /** 最后登录 IP */
    @Schema(description = "最后登录 IP")
    private String loginIp;

    /** 最后登录时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "最后登录时间")
    private LocalDateTime loginDate;

    /** 备注 */
    @Schema(description = "备注")
    private String remark;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 已分配角色 ID 集合，仅详情接口返回 */
    @Schema(description = "已分配角色 ID 集合，仅详情接口返回")
    private List<Long> roleIds;

    /** 已分配角色名称集合，仅详情接口返回 */
    @Schema(description = "已分配角色名称集合，仅详情接口返回")
    private List<String> roleNames;
}
