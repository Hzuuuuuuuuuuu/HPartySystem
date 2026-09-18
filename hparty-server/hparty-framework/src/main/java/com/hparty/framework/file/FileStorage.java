package com.hparty.framework.file;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储抽象。
 * <p>当前实现为本地磁盘 {@link LocalFileStorage}，如需切换 MinIO / OSS，
 * 新增一个实现类并调整 {@code hparty.file.storage} 配置即可，业务代码无需改动。</p>
 */
public interface FileStorage {

    /**
     * 保存文件。
     *
     * @param file    上传的文件
     * @param bizType 业务类型，决定子目录，如 dev_material / meeting / avatar
     * @return 存储结果（相对路径与访问 URL）
     */
    StoredFile store(MultipartFile file, String bizType);

    /**
     * 删除文件。
     *
     * @param relativePath {@link #store} 返回的相对路径
     */
    void delete(String relativePath);

    /**
     * 读取文件字节，供下载/预览使用。
     */
    byte[] read(String relativePath);

    /** 当前存储类型标识 */
    String storageType();

    /**
     * 存储结果。
     *
     * @param relativePath 相对路径，入库保存
     * @param url          访问 URL
     * @param originalName 原始文件名
     * @param size         文件字节数
     */
    record StoredFile(String relativePath, String url, String originalName, long size) {
    }
}
