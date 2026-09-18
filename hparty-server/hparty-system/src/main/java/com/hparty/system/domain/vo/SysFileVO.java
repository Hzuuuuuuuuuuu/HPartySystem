package com.hparty.system.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hparty.system.domain.entity.SysFile;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件展示对象。
 */
@Data
public class SysFileVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 文件ID */
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

    /** 文件大小可读文本，如 1.2 MB */
    private String fileSizeText;

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

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 由实体转换 */
    public static SysFileVO of(SysFile entity) {
        if (entity == null) {
            return null;
        }
        SysFileVO vo = new SysFileVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setFileSizeText(formatSize(entity.getFileSize()));
        return vo;
    }

    /** 字节数转可读文本 */
    private static String formatSize(Long size) {
        if (size == null || size < 0) {
            return "-";
        }
        if (size < 1024) {
            return size + " B";
        }
        double kb = size / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.1f MB", mb);
        }
        return String.format("%.1f GB", mb / 1024.0);
    }
}
