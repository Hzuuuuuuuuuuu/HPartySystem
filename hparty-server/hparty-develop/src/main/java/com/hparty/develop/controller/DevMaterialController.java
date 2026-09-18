package com.hparty.develop.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.hparty.common.core.R;
import com.hparty.develop.service.DevMaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 发展党员个人材料提交接口。
 *
 * <p>这里故意不使用单一 {@code @SaCheckPermission}：APPLICANT 与 TRAINER 的合法提交能力来自
 * “本人关系/培养联系人关系”，不能通过给低权限用户授予宽泛管理权限来实现。最终授权统一由
 * {@link DevMaterialService} 进行资源级判定。</p>
 */
@Tag(name = "12-发展党员材料提交")
@RestController
@RequestMapping("/develop/applicant/{applicantId}/materials")
@RequiredArgsConstructor
public class DevMaterialController {

    private final DevMaterialService materialService;

    @Operation(summary = "上传/归档个人发展材料")
    @SaCheckLogin
    @PostMapping("/{templateId}")
    public R<Long> upload(@PathVariable Long applicantId,
                          @PathVariable Long templateId,
                          @RequestParam("file") MultipartFile file) {
        return R.ok("材料上传成功", materialService.upload(applicantId, templateId, file));
    }

    @Operation(summary = "删除个人发展材料")
    @SaCheckLogin
    @DeleteMapping("/{materialId}")
    public R<Void> delete(@PathVariable Long applicantId,
                          @PathVariable Long materialId) {
        materialService.delete(applicantId, materialId);
        return R.ok("材料删除成功", null);
    }
}
