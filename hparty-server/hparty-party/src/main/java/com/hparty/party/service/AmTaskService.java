package com.hparty.party.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hparty.common.exception.BizException;
import com.hparty.framework.datascope.DataScopeHelper;
import com.hparty.framework.file.FileStorage;
import com.hparty.framework.security.LoginUser;
import com.hparty.framework.security.SecurityUtils;
import com.hparty.party.domain.entity.AmTask;
import com.hparty.party.domain.entity.AmTaskSubmit;
import com.hparty.party.domain.entity.PartyFile;
import com.hparty.party.mapper.AmTaskMapper;
import com.hparty.party.mapper.AmTaskSubmitMapper;
import com.hparty.party.mapper.PartyFileMapper;
import com.hparty.party.mapper.PartyLookupMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动任务通知服务（对应图1 底部的任务卡与「上传资料」）。
 *
 * <p><b>数据权限</b>：列表查询走 {@link DataScopeHelper#apply} 追加
 * {@code publish_org_id} 范围条件（{@code am_task} 的组织字段是 {@code publish_org_id}，
 * 不是默认的 {@code org_id}）；按主键的详情、修改、删除、提交则统一用
 * {@link DataScopeHelper#canAccessOrg(Long)} 做越权校验 —— 只靠列表条件拦不住
 * 直接构造 ID 的请求。</p>
 */
@Service
@RequiredArgsConstructor
public class AmTaskService {

    /** 任务材料在 sys_file 中的业务类型，决定磁盘子目录 */
    public static final String TASK_BIZ_TYPE = "task_material";

    private final AmTaskMapper taskMapper;
    private final AmTaskSubmitMapper submitMapper;
    private final PartyLookupMapper lookupMapper;
    private final PartyFileMapper fileMapper;
    private final FileStorage fileStorage;

    /**
     * 任务列表。
     * <p>访问范围以「发布组织」为准：上级党委发布的任务，下辖支部都能看到。</p>
     */
    public List<AmTask> list(String taskType, Integer status) {
        var wrapper = new LambdaQueryWrapper<AmTask>().orderByDesc(AmTask::getPublishTime);
        // am_task 的组织字段是 publish_org_id，不是默认的 org_id
        DataScopeHelper.apply(wrapper, "publish_org_id", null);

        if (StrUtil.isNotBlank(taskType)) {
            wrapper.eq(AmTask::getTaskType, taskType);
        }
        if (status != null) {
            wrapper.eq(AmTask::getStatus, status);
        }
        return taskMapper.selectList(wrapper);
    }

    /**
     * 任务详情。
     *
     * @param taskId 任务ID
     * @return 任务实体
     * @throws BizException 任务不存在，或发布组织不在当前用户数据权限内
     */
    public AmTask get(Long taskId) {
        AmTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException("任务不存在");
        }
        if (!DataScopeHelper.canAccessOrg(task.getPublishOrgId())) {
            throw BizException.forbidden("无权操作其他党组织的任务");
        }
        return task;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long add(AmTask task) {
        if (StrUtil.isBlank(task.getTitle())) {
            throw new BizException("请填写任务标题");
        }
        task.setTaskId(null);

        LoginUser user = SecurityUtils.getLoginUser();
        if (task.getPublishOrgId() == null) {
            task.setPublishOrgId(user.getOrgId());
        } else if (!DataScopeHelper.canAccessOrg(task.getPublishOrgId())) {
            // 不允许把任务挂到别人名下
            throw BizException.forbidden("无权操作其他党组织的任务");
        }
        if (StrUtil.isBlank(task.getPublishOrgName())) {
            task.setPublishOrgName(lookupMapper.selectOrgName(task.getPublishOrgId()));
        }
        task.setStatus(task.getStatus() == null ? 1 : task.getStatus());
        task.setPublishBy(user.getUsername());
        task.setPublishTime(LocalDateTime.now());
        taskMapper.insert(task);
        return task.getTaskId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(AmTask task) {
        if (task.getTaskId() == null) {
            throw new BizException("任务ID不能为空");
        }
        // get 已做越权校验；同时把发布组织固定为库中原值，
        // 否则调用方可以通过修改 publishOrgId 把任务「过户」到别的组织
        AmTask exists = get(task.getTaskId());
        task.setPublishOrgId(exists.getPublishOrgId());
        task.setPublishOrgName(exists.getPublishOrgName());
        taskMapper.updateById(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(Long taskId) {
        get(taskId);
        taskMapper.deleteById(taskId);
    }

    /**
     * 支部上传任务材料。
     *
     * <p>两种入参方式，同时传时以 {@code file} 为准：</p>
     * <ul>
     *   <li>{@code file} —— 前端 multipart 直传（{@code TaskNoticeList.tsx} 用的就是这种）；
     *       文件先经 {@link FileStorage} 落盘，再登记到 {@code sys_file}，最后把生成的
     *       fileId / fileUrl 写进提交记录。</li>
     *   <li>{@code fileId} / {@code fileUrl} —— 调用方已自行通过 {@code /file/upload} 上传完毕，
     *       这里只做登记，兼容旧调用方式。</li>
     * </ul>
     *
     * @param taskId  任务ID
     * @param file    上传的文件，可为空
     * @param fileId  已上传文件的ID，可为空
     * @param fileUrl 已上传文件的URL，可为空
     * @param remark  备注
     * @return 提交记录ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long submit(Long taskId, MultipartFile file, Long fileId, String fileUrl, String remark) {
        // get 内含组织越权校验
        AmTask task = get(taskId);

        if (file != null && !file.isEmpty()) {
            FileStorage.StoredFile stored = fileStorage.store(file, TASK_BIZ_TYPE);
            fileId = registerFile(stored, taskId);
            fileUrl = stored.url();
        }

        AmTaskSubmit submit = new AmTaskSubmit();
        submit.setTaskId(taskId);
        submit.setOrgId(SecurityUtils.getOrgId());
        submit.setFileId(fileId);
        submit.setFileUrl(fileUrl);
        submit.setRemark(remark);
        submit.setSubmitBy(SecurityUtils.getUsername());
        submit.setSubmitTime(LocalDateTime.now());
        submitMapper.insert(submit);

        // 首次提交时把任务标记为已开始办理
        if (task.getStatus() != null && task.getStatus() == 1) {
            AmTask update = new AmTask();
            update.setTaskId(taskId);
            update.setStatus(2);
            taskMapper.updateById(update);
        }
        return submit.getSubmitId();
    }

    /** 某任务的提交记录 */
    public List<AmTaskSubmit> listSubmits(Long taskId) {
        // 先校验父任务的可见性，避免通过提交记录接口旁路拿到别的组织的材料
        get(taskId);
        return submitMapper.selectList(new LambdaQueryWrapper<AmTaskSubmit>()
                .eq(AmTaskSubmit::getTaskId, taskId)
                .orderByDesc(AmTaskSubmit::getSubmitTime));
    }

    // ==================== 私有方法 ====================

    /**
     * 把已落盘的文件登记到 {@code sys_file}。
     *
     * <p>字段口径与 {@code com.hparty.system.service.SysFileService#upload} 保持一致
     * （含 org_id / upload_by），这样 system 模块的文件接口对本条记录同样能正确鉴权。</p>
     *
     * @param stored 存储结果
     * @param bizId  业务ID，这里取任务ID
     * @return 新生成的 fileId
     */
    private Long registerFile(FileStorage.StoredFile stored, Long bizId) {
        PartyFile record = new PartyFile();
        record.setFileName(stored.originalName());
        record.setFilePath(stored.relativePath());
        record.setFileUrl(stored.url());
        record.setFileSuffix(extName(stored.originalName()));
        record.setFileSize(stored.size());
        record.setContentType(MediaTypeFactory.getMediaType(stored.originalName())
                .map(MediaType::toString)
                .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE));
        record.setStorageType(fileStorage.storageType());
        record.setBizType(TASK_BIZ_TYPE);
        record.setBizId(bizId);
        // 与 SysFileService.applyUploadContext 一致：归属上传人所在组织
        record.setOrgId(SecurityUtils.getOrgId());
        record.setUploadBy(SecurityUtils.getUserId());
        fileMapper.insertFile(record);
        return record.getFileId();
    }

    /** 取小写扩展名（不含点），无扩展名时返回 null */
    private String extName(String fileName) {
        if (StrUtil.isBlank(fileName)) {
            return null;
        }
        int index = fileName.lastIndexOf('.');
        return index < 0 || index == fileName.length() - 1
                ? null : fileName.substring(index + 1).toLowerCase();
    }
}
