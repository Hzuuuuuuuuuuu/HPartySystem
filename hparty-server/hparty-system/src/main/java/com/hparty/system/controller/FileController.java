package com.hparty.system.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.hparty.common.core.R;
import com.hparty.system.domain.vo.SysFileVO;
import com.hparty.system.service.SysFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文件上传下载接口。
 * <p>预览、下载、查询和删除都要求登录。预览 URL 本身不再作为访问凭证；
 * 文件是否可读由 {@link SysFileService} 统一判断：普通文件按 {@code sys_file.org_id} 数据范围，
 * 具备业务策略的文件（如发展党员个人材料）还会进一步校验具体业务资源关系。</p>
 * <p>文件接口未加 {@code @SaCheckPermission}：文件是各业务模块（发展党员材料、会议材料、头像等）共用的基础设施，
 * 而初始化脚本中并未预置文件相关权限标识，写死权限会导致所有业务模块的上传全部被拒。</p>
 */
@Tag(name = "文件管理")
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final SysFileService fileService;

    /**
     * 上传单个文件。
     *
     * @param file    文件，表单字段名 file
     * @param bizType 业务类型：dev_material / meeting / avatar 等，可空
     * @param bizId   业务ID，可空
     * @return 文件信息（含访问 URL）
     */
    @Operation(summary = "上传单个文件")
    @PostMapping("/upload")
    public R<SysFileVO> upload(@RequestParam("file") MultipartFile file,
                               @RequestParam(required = false) String bizType,
                               @RequestParam(required = false) Long bizId) {
        return R.ok(fileService.upload(file, bizType, bizId));
    }

    /**
     * 批量上传文件。
     *
     * @param files   文件数组，表单字段名 files
     * @param bizType 业务类型，可空
     * @param bizId   业务ID，可空
     * @return 文件信息列表，顺序与入参一致
     */
    @Operation(summary = "批量上传文件")
    @PostMapping("/uploadBatch")
    public R<List<SysFileVO>> uploadBatch(@RequestParam("files") MultipartFile[] files,
                                          @RequestParam(required = false) String bizType,
                                          @RequestParam(required = false) Long bizId) {
        return R.ok(fileService.uploadBatch(files, bizType, bizId));
    }

    /**
     * 在线预览文件（inline，图片 / PDF 可直接在浏览器显示）。
     *
     * @param bizType  业务类型
     * @param date     日期目录，格式 yyyyMMdd
     * @param fileName 存储文件名
     * @return 文件字节流
     */
    @Operation(summary = "在线预览文件")
    @SaCheckLogin
    @GetMapping("/preview/{bizType}/{date}/{fileName}")
    public ResponseEntity<byte[]> preview(@PathVariable String bizType,
                                          @PathVariable String date,
                                          @PathVariable String fileName) {
        return buildResponse(fileService.preview(bizType, date, fileName), true);
    }

    /**
     * 下载文件（attachment，使用原始文件名）。
     * <p>必须登录，且文件归属组织需在当前用户数据权限内（校验在
     * {@link SysFileService#download(Long)} 中）。{@code @SaCheckLogin} 与全局拦截器重复，
     * 但显式标注可以避免将来有人把 {@code /file/**} 加进白名单时把下载也一起放开。</p>
     *
     * @param fileId 文件ID
     * @return 文件字节流
     */
    @Operation(summary = "下载文件")
    @SaCheckLogin
    @GetMapping("/download/{fileId}")
    public ResponseEntity<byte[]> download(@PathVariable Long fileId) {
        return buildResponse(fileService.download(fileId), false);
    }

    /**
     * 查询文件信息。
     * <p>必须登录，且文件归属组织需在当前用户数据权限内。</p>
     *
     * @param fileId 文件ID
     * @return 文件信息
     */
    @Operation(summary = "查询文件信息")
    @SaCheckLogin
    @GetMapping("/{fileId}")
    public R<SysFileVO> getFile(@PathVariable Long fileId) {
        return R.ok(fileService.getFile(fileId));
    }

    /**
     * 删除文件（同时删除磁盘文件与数据库记录）。
     *
     * @param fileId 文件ID
     * @return 操作结果
     */
    @Operation(summary = "删除文件")
    @DeleteMapping("/{fileId}")
    public R<Void> delete(@PathVariable Long fileId) {
        return R.toR(fileService.delete(fileId));
    }

    /**
     * 组装文件响应：inline 用于预览、attachment 用于下载，文件名按 UTF-8 编码避免中文乱码。
     *
     * @param content 文件内容
     * @param inline  true=浏览器内联展示
     * @return 字节流响应
     */
    private ResponseEntity<byte[]> buildResponse(SysFileService.FileContent content, boolean inline) {
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(content.contentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        String fileName = content.fileName() == null ? "file" : content.fileName();
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(fileName, StandardCharsets.UTF_8)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDisposition(disposition);
        headers.setContentLength(content.size());
        return new ResponseEntity<>(content.bytes(), headers, HttpStatus.OK);
    }
}
