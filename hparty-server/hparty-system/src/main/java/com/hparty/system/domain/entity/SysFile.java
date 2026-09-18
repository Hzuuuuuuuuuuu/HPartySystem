package com.hparty.system.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件
 */
@Data
@TableName("sys_file")
public class SysFile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 文件ID */
    @TableId(value = "file_id", type = IdType.AUTO)
    private Long fileId;

    /** 原始文件名 */
    private String fileName;

    /** 存储相对路径 */
    private String filePath;

    /** 访问URL */
    private String fileUrl;

    /** 文件后缀 */
    private String fileSuffix;

    /** 文件大小(字节) */
    private Long fileSize;

    /** MIME类型 */
    private String contentType;

    /** 存储类型：local/minio/oss */
    private String storageType;

    /** 业务类型：dev_material/meeting/avatar */
    private String bizType;

    /** 业务ID */
    private Long bizId;

    /** 所属组织 */
    private Long orgId;

    /** 上传人 */
    private Long uploadBy;

    /** 删除标志：0=存在 1=删除 */
    @TableLogic
    @TableField(value = "del_flag")
    private Integer delFlag;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
