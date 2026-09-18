package com.hparty.develop.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.util.StrUtil;
import com.hparty.common.core.R;
import com.hparty.common.exception.BizException;
import com.hparty.develop.domain.entity.DevMaterialTemplate;
import com.hparty.develop.service.DevMaterialTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 发展党员材料模板接口（《广西发展党员工作手册》50 份表格的下载）。
 *
 * <p>模板文件是二进制，只做搬运与流式返回，不解析内容。</p>
 */
@Slf4j
@Tag(name = "12-发展党员材料")
@RestController
@RequestMapping("/develop/material/template")
@RequiredArgsConstructor
public class DevMaterialTemplateController {

    /** 空白模板所在目录（classpath） */
    private static final String BLANK_DIR = "material-templates/blank/";
    /** 填写样例所在目录（classpath） */
    private static final String SAMPLE_DIR = "material-templates/sample/";

    /** 下载类型：空白模板 */
    private static final String TYPE_BLANK = "blank";
    /** 下载类型：填写样例 */
    private static final String TYPE_SAMPLE = "sample";

    private final DevMaterialTemplateService templateService;

    @Operation(summary = "材料模板列表（可按阶段/步骤过滤）")
    @SaCheckPermission("develop:applicant:list")
    @GetMapping("/list")
    public R<List<DevMaterialTemplate>> list(@RequestParam(required = false) String stageCode,
                                             @RequestParam(required = false) String stepCode) {
        return R.ok(templateService.listBy(stageCode, stepCode));
    }

    @Operation(summary = "某步骤需要的材料模板")
    @SaCheckPermission("develop:applicant:list")
    @GetMapping("/step/{stepCode}")
    public R<List<DevMaterialTemplate>> listByStep(@PathVariable String stepCode) {
        return R.ok(templateService.listByStep(stepCode));
    }

    @Operation(summary = "下载材料模板（type=blank 空白模板 / type=sample 填写样例）")
    @SaCheckPermission("develop:applicant:list")
    @GetMapping("/{templateId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long templateId,
                                             @RequestParam(defaultValue = TYPE_BLANK) String type) {
        DevMaterialTemplate template = templateService.getById(templateId);

        boolean sample = TYPE_SAMPLE.equalsIgnoreCase(type);
        if (!sample && !TYPE_BLANK.equalsIgnoreCase(type)) {
            throw new BizException("下载类型只能是 blank 或 sample");
        }

        String fileName = sample ? template.getSampleFile() : template.getBlankFile();
        if (StrUtil.isBlank(fileName)) {
            throw new BizException(sample ? "该材料未提供填写样例：" + template.getTemplateName()
                    : "该材料未提供空白模板：" + template.getTemplateName());
        }

        // 文件名只允许取最后一段，避免 ../ 之类的路径穿越（数据虽由种子脚本写入，仍做防御）
        String safeName = fileName.replace('\\', '/');
        if (safeName.contains("/")) {
            safeName = safeName.substring(safeName.lastIndexOf('/') + 1);
        }
        if (StrUtil.isBlank(safeName) || safeName.contains("..")) {
            throw new BizException("模板文件不存在：" + fileName);
        }

        ClassPathResource resource = new ClassPathResource((sample ? SAMPLE_DIR : BLANK_DIR) + safeName);

        // 中文文件名必须用 RFC 5987 的 filename*=UTF-8'' 形式，否则浏览器侧乱码。
        // URLEncoder 把空格编成 '+'，而 filename* 里 '+' 是字面量，需换成 %20。
        String downloadName = URLEncoder.encode(template.getTemplateName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        String suffix = safeName.contains(".")
                ? safeName.substring(safeName.lastIndexOf('.') + 1) : "";

        // 同时给出 filename*（RFC 5987，中文名，现代浏览器优先采用）
        // 与 filename（ASCII 兜底，编号本身就是 ASCII）。
        // 只给 filename* 的话，不解析该参数的客户端（如部分版本的 curl -OJ）
        // 会退化成用 URL 末段当文件名，拿到一个无意义的名字。
        String asciiFallback = template.getTemplateCode() + "." + suffix;
        String encodedAscii = URLEncoder.encode(asciiFallback, StandardCharsets.UTF_8).replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaTypeOf(suffix));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + encodedAscii + "\"; filename*=UTF-8''"
                        + downloadName + "." + suffix);

        Resource body;
        try {
            // 注意：这里的流不能自己关，要留给 Spring 写响应体时关闭
            body = new InputStreamResource(resource.getInputStream());
            headers.setContentLength(resource.contentLength());
        } catch (IOException e) {
            log.warn("材料模板文件读取失败：{}", resource.getPath(), e);
            throw new BizException("模板文件不存在：" + fileName);
        }

        return ResponseEntity.ok().headers(headers).body(body);
    }

    /** 按扩展名推断 Content-Type */
    private MediaType mediaTypeOf(String suffix) {
        return switch (suffix == null ? "" : suffix.toLowerCase()) {
            case "doc" -> MediaType.parseMediaType("application/msword");
            case "docx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "xls" -> MediaType.parseMediaType("application/vnd.ms-excel");
            case "xlsx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
