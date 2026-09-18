package com.hparty.framework.file;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件存储配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "hparty.file")
public class FileStorageProperties {

    /** 存储类型：local / minio / oss */
    private String storage = "local";

    /** 本地存储根目录 */
    private String localPath = "D:/hparty/upload";

    /** 文件访问前缀 */
    private String urlPrefix = "/api/file/preview";

    /** 单文件大小上限（MB） */
    private long maxSizeMb = 50;

    /** 允许的上传扩展名 */
    private String allowedExtensions = "jpg,jpeg,png,gif,bmp,webp,pdf,doc,docx,xls,xlsx,ppt,pptx,txt,zip,rar,mp4,mp3";
}
