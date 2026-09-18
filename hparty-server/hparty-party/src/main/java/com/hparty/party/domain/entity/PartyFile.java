package com.hparty.party.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文件记录（{@code sys_file} 的党务域投影）。
 *
 * <p><b>为什么这里又写了一个 sys_file 的实体</b>：{@code hparty-party} 不依赖
 * {@code hparty-system}（避免模块间循环依赖，见 {@link com.hparty.party.mapper.PartyLookupMapper} 的说明），
 * 拿不到 {@code com.hparty.system.domain.entity.SysFile}。但党务域上传的附件必须登记到
 * {@code sys_file}，否则文件只落在磁盘上、库里没有任何记录 —— 既无法通过
 * {@code /file/download/{fileId}} 下载，{@code delete} 也清不掉，还会变成永久孤儿文件。
 *
 * <p>因此这里只保留写入 {@code sys_file} 所需的最小字段集，配合
 * {@link com.hparty.party.mapper.PartyFileMapper} 的注解 SQL 使用。
 * 字段含义与 {@code SysFile} 完全一致，改动 {@code sys_file} 表结构时两边都要同步。</p>
 */
@Data
@TableName("sys_file")
public class PartyFile implements Serializable {

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

    /** 业务类型：dev_material/meeting/task_material 等 */
    private String bizType;

    /** 业务ID */
    private Long bizId;

    /** 所属组织 */
    private Long orgId;

    /** 上传人（sys_user.user_id） */
    private Long uploadBy;
}
