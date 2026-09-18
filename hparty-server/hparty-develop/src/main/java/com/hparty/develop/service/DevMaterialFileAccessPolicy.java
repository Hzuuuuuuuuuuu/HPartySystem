package com.hparty.develop.service;

import cn.hutool.core.util.StrUtil;
import com.hparty.develop.domain.entity.DevApplicant;
import com.hparty.develop.mapper.DevApplicantMapper;
import com.hparty.framework.datascope.DataScopeHelper;
import com.hparty.framework.security.LoginUser;
import com.hparty.system.domain.entity.SysFile;
import com.hparty.system.service.SysFileAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Objects;

/**
 * 发展党员材料文件的资源级读取策略。
 *
 * <p>{@code sys_file.biz_id} 对 dev_material 文件保存的是 applicantId，
 * 因此即使文件 URL 泄露，也必须再次回到申请人资源上判断本人、培养联系人
 * 或管理人员的数据权限，不能只因为“同一个支部”就放行。</p>
 */
@Component
@RequiredArgsConstructor
public class DevMaterialFileAccessPolicy implements SysFileAccessPolicy {

    private static final String BIZ_TYPE = "dev_material";

    private final DevApplicantMapper applicantMapper;

    @Override
    public boolean supports(SysFile file) {
        return file != null && BIZ_TYPE.equalsIgnoreCase(file.getBizType());
    }

    @Override
    public boolean canRead(SysFile file, LoginUser user) {
        if (file == null || user == null || file.getBizId() == null) {
            return false;
        }
        DevApplicant applicant = applicantMapper.selectById(file.getBizId());
        if (applicant == null) {
            return false;
        }
        if (user.isSuperAdmin()) {
            return true;
        }

        // 申请人本人：最小、最明确的个人资源授权。
        if (user.getPersonId() != null && Objects.equals(user.getPersonId(), applicant.getPersonId())) {
            return true;
        }

        // 培养联系人：精确业务关系授权，不扩大为申请人的通用数据访问范围。
        if (user.getPersonId() != null && isTrainer(applicant.getTrainerIds(), user.getPersonId())) {
            return true;
        }

        // 党务管理人员：既要有发展党员详情/材料管理功能权限，又要通过资源数据范围。
        boolean hasBusinessPermission = user.hasPerm("develop:applicant:detail")
                || user.hasPerm("develop:material:submit");
        return hasBusinessPermission
                && DataScopeHelper.canAccessData(applicant.getOrgId(), applicant.getPersonId());
    }

    /** 发展党员材料禁止绕过 dev_material 业务记录直接从通用文件接口删除。 */
    @Override
    public boolean canDeleteDirectly(SysFile file, LoginUser user) {
        return false;
    }

    private boolean isTrainer(String trainerIds, Long personId) {
        if (StrUtil.isBlank(trainerIds) || personId == null) {
            return false;
        }
        return Arrays.stream(trainerIds.split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .anyMatch(value -> value.equals(String.valueOf(personId)));
    }
}
