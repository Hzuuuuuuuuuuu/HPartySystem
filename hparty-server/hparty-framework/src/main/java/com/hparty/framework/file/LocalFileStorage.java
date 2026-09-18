package com.hparty.framework.file;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.hparty.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

/**
 * 本地磁盘文件存储。
 * <p>目录结构：{@code {localPath}/{bizType}/{yyyyMMdd}/{uuid}.{ext}}</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hparty.file.storage", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    private final FileStorageProperties properties;

    @Override
    public StoredFile store(MultipartFile file, String bizType) {
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件不能为空");
        }

        String originalName = file.getOriginalFilename();
        if (StrUtil.isBlank(originalName)) {
            throw new BizException("无法获取文件名");
        }

        // 大小校验
        long maxBytes = properties.getMaxSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BizException("文件大小超过限制：" + properties.getMaxSizeMb() + "MB");
        }

        // 扩展名校验，防重命名攻击
        String ext = FileUtil.extName(originalName);
        if (StrUtil.isBlank(ext) || !isAllowed(ext)) {
            throw new BizException("不支持的文件类型：" + ext);
        }

        String safeBizType = StrUtil.isBlank(bizType) ? "common" : bizType.replaceAll("[^a-zA-Z0-9_]", "");
        String dateDir = DateUtil.format(new Date(), "yyyyMMdd");
        String fileName = IdUtil.fastSimpleUUID() + "." + ext.toLowerCase();
        String relativePath = safeBizType + "/" + dateDir + "/" + fileName;

        Path target = Paths.get(properties.getLocalPath(), relativePath).toAbsolutePath().normalize();

        // 防止路径穿越
        Path root = Paths.get(properties.getLocalPath()).toAbsolutePath().normalize();
        if (!target.startsWith(root)) {
            throw new BizException("非法的文件路径");
        }

        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target.toFile());
        } catch (IOException e) {
            log.error("文件保存失败: {}", target, e);
            throw new BizException("文件保存失败：" + e.getMessage());
        }

        return new StoredFile(relativePath, buildUrl(relativePath), originalName, file.getSize());
    }

    @Override
    public void delete(String relativePath) {
        if (StrUtil.isBlank(relativePath)) {
            return;
        }
        try {
            Path target = Paths.get(properties.getLocalPath(), relativePath).toAbsolutePath().normalize();
            Path root = Paths.get(properties.getLocalPath()).toAbsolutePath().normalize();
            if (target.startsWith(root)) {
                Files.deleteIfExists(target);
            }
        } catch (IOException e) {
            log.warn("文件删除失败: {}", relativePath, e);
        }
    }

    @Override
    public byte[] read(String relativePath) {
        try {
            Path target = Paths.get(properties.getLocalPath(), relativePath).toAbsolutePath().normalize();
            Path root = Paths.get(properties.getLocalPath()).toAbsolutePath().normalize();
            if (!target.startsWith(root)) {
                throw new BizException("非法的文件路径");
            }
            File f = target.toFile();
            if (!f.exists()) {
                throw new BizException("文件不存在");
            }
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new BizException("文件读取失败：" + e.getMessage());
        }
    }

    @Override
    public String storageType() {
        return "local";
    }

    private String buildUrl(String relativePath) {
        String prefix = StrUtil.removeSuffix(properties.getUrlPrefix(), "/");
        return prefix + "/" + relativePath;
    }

    private boolean isAllowed(String ext) {
        String allowed = properties.getAllowedExtensions();
        if (StrUtil.isBlank(allowed)) {
            return true;
        }
        String lower = ext.toLowerCase();
        for (String a : allowed.split(",")) {
            if (a.trim().equalsIgnoreCase(lower)) {
                return true;
            }
        }
        return false;
    }
}
