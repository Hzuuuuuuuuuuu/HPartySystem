package com.hparty.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.hparty.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 系统用户
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    /** 用户ID */
    @TableId(value = "user_id", type = IdType.AUTO)
    private Long userId;

    /** 登录账号 */
    private String username;

    /** 密码（BCrypt） */
    private String password;

    /** 昵称 */
    private String nickName;

    /** 关联 party_person.person_id */
    private Long personId;

    /** 所属党组织 */
    private Long orgId;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 头像URL */
    private String avatar;

    /** 性别：0=未知 1=男 2=女 */
    private Integer sex;

    /** 状态：0=停用 1=正常 */
    private Integer status;

    /** 最后登录IP */
    private String loginIp;

    /** 最后登录时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime loginDate;

    /** 密码最后更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime pwdUpdateDate;

    /** 备注 */
    private String remark;
}
