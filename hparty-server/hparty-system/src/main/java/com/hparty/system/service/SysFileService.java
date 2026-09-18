package com.hparty.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hparty.common.exception.BizException;
import com.hparty.framework.datascope.DataScopeHelper;
import com.hparty.framework.file.FileStorage;
import com.hparty.framework.file.FileStorageProperties;
import com.hparty.framework.security.LoginUser;
import com.hparty.framework.security.SecurityUtils;
import com.hparty.system.domain.entity.SysFile;
import com.hparty.system.domain.vo.SysFileVO;
import com.hparty.system.mapper.SysFileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 文件服务。
 * <p>磁盘读写全部委托给 {@link FileStorage} 实现（其中已包含大小/扩展名校验与路径穿越防护），
 * 本服务只负责把存储结果落库到 {@code sys_file}，以及按业务维度读取、删除文件。</p>
 * <p>注意：本地磁盘存储下，批量上传中途失败会回滚数据库记录，但已写入磁盘的文件不会被回收，
 * 需要由运维侧定期清理孤儿文件。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysFileService {

    /** 业务类型、日期目录：仅允许字母数字下划线短横线 */
    private static final Pattern DIR_SEGMENT = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    /** 日期目录：yyyyMMdd */
    private static final Pattern DATE_SEGMENT = Pattern.compile("^\\d{8}$");

    /** 文件名：uuid.扩展名，禁止路径分隔符与 .. */
    private static final Pattern FILE_SEGMENT = Pattern.compile("^[A-Za-z0-9_-]{1,64}(\\.[A-Za-z0-9]{1,16})?$");

    private final SysFileMapper fileMapper;

    private final FileStorage fileStorage;

    private final FileStorageProperties fileProperties;

    /** 可选的业务资源级文件访问策略；未命中时退回通用组织数据权限。 */
    private final List<SysFileAccessPolicy> accessPolicies;

    /**
     * 上传单个文件并登记到文件表。
     *
     * @param file    上传的文件
     * @param bizType 业务类型，如 dev_material / meeting / avatar，为空时归入 common
     * @param bizId   业务ID，可为空
     * @return 文件信息（含访问 URL）
     */
    @Transactional(rollbackFor = Exception.class)
    public SysFileVO upload(MultipartFile file, String bizType, Long bizId) {
        return uploadInternal(file, bizType, bizId, null);
    }

    /**
     * 已完成业务资源授权后，把文件归属到目标业务组织，而不是上传人所在组织。
     * <p>典型场景：党委用户给下级支部的发展对象上传党委材料。若仍把文件归属到党委，
     * 申请人本人后续会因文件组织与本人组织不一致而无法预览。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public SysFileVO uploadForAuthorizedOrg(MultipartFile file, String bizType, Long bizId, Long ownerOrgId) {
        BizException.throwIf(ownerOrgId == null, "文件归属组织不能为空");
        return uploadInternal(file, bizType, bizId, ownerOrgId);
    }

    private SysFileVO uploadInternal(MultipartFile file, String bizType, Long bizId, Long ownerOrgId) {
        BizException.throwIf(file == null || file.isEmpty(), "上传文件不能为空");

        FileStorage.StoredFile stored = fileStorage.store(file, bizType);
        SysFile entity = new SysFile();
        entity.setFileName(stored.originalName());
        entity.setFilePath(stored.relativePath());
        entity.setFileUrl(stored.url());
        entity.setFileSuffix(extName(stored.originalName()));
        entity.setFileSize(stored.size());
        entity.setContentType(resolveContentType(file.getContentType(), stored.originalName()));
        entity.setStorageType(fileStorage.storageType());
        entity.setBizType(normalizeBizType(bizType));
        entity.setBizId(bizId);
        applyUploadContext(entity);
        if (ownerOrgId != null) {
            entity.setOrgId(ownerOrgId);
        }

        fileMapper.insert(entity);
        log.info("文件上传成功：fileId={} name={} size={}", entity.getFileId(), entity.getFileName(), entity.getFileSize());
        return SysFileVO.of(entity);
    }

    /**
     * 业务层在后续数据库归档失败时清理刚写入的物理文件。
     * <p>调用方所在事务会回滚 {@code sys_file} 记录；这里仅补偿不可事务化的存储介质，
     * 不接受任意路径，只使用本服务刚返回的 {@link SysFileVO#getFilePath()}。</p>
     */
    public void cleanupUploadedFile(SysFileVO uploaded) {
        if (uploaded == null || !StringUtils.hasText(uploaded.getFilePath())) {
            return;
        }
        try {
            fileStorage.delete(uploaded.getFilePath());
        } catch (Exception e) {
            log.warn("回滚业务归档时清理上传文件失败：fileId={} path={}",
                    uploaded.getFileId(), uploaded.getFilePath(), e);
        }
    }

    /**
     * 批量上传文件。
     *
     * @param files   上传的文件数组
     * @param bizType 业务类型
     * @param bizId   业务ID，可为空
     * @return 文件信息列表，顺序与入参一致
     */
    @Transactional(rollbackFor = Exception.class)
    public List<SysFileVO> uploadBatch(MultipartFile[] files, String bizType, Long bizId) {
        BizException.throwIf(files == null || files.length == 0, "上传文件不能为空");

        List<SysFileVO> result = new ArrayList<>(files.length);
        for (MultipartFile file : files) {
            result.add(upload(file, bizType, bizId));
        }
        return result;
    }

    /**
     * 查询文件信息。
     * <p>文件归属组织不在当前用户数据权限内时拒绝访问。</p>
     *
     * @param fileId 文件ID
     * @return 文件信息
     */
    public SysFileVO getFile(Long fileId) {
        BizException.throwIf(fileId == null, "文件ID不能为空");
        SysFile entity = requireReadable(fileId);
        return SysFileVO.of(entity);
    }

    /**
     * 预览文件：按存储相对路径（bizType/yyyyMMdd/文件名）读取字节流。
     * <p>预览必须绑定有效 {@code sys_file} 元数据，并按文件所属组织做数据权限校验；
     * 磁盘存在但数据库无记录的孤儿文件不得通过预览接口读取。</p>
     *
     * @param bizType  业务类型
     * @param date     日期目录，格式 yyyyMMdd
     * @param fileName 存储文件名
     * @return 文件内容（含原始文件名与 MIME 类型）
     */
    public FileContent preview(String bizType, String date, String fileName) {
        String relativePath = buildRelativePath(bizType, date, fileName);

        QueryWrapper<SysFile> wrapper = new QueryWrapper<>();
        wrapper.eq("file_path", relativePath);
        SysFile record = fileMapper.selectList(wrapper).stream().findFirst().orElse(null);
        BizException.throwIf(record == null, "文件不存在");
        checkReadAccess(record, "无权访问该文件");
        return readEntity(record);
    }

    /**
     * 下载文件：按文件ID读取原始内容与原始文件名。
     * <p>与 {@link #delete(Long)} 对齐，下载前先做组织越权校验 ——
     * 否则任意登录账号只要猜到（或从列表里拿到）fileId 就能取走别的组织的文件字节。</p>
     *
     * @param fileId 文件ID
     * @return 文件内容
     */
    public FileContent download(Long fileId) {
        BizException.throwIf(fileId == null, "文件ID不能为空");
        SysFile entity = requireReadable(fileId);
        return readEntity(entity);
    }

    /**
     * 删除文件：同时删除磁盘文件与数据库记录（逻辑删除）。
     * <p>文件归属组织不在当前用户数据权限内时拒绝删除。</p>
     *
     * @param fileId 文件ID
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long fileId) {
        BizException.throwIf(fileId == null, "文件ID不能为空");
        SysFile entity = fileMapper.selectById(fileId);
        BizException.throwIf(entity == null, "文件不存在");

        SysFileAccessPolicy policy = findAccessPolicy(entity);
        if (policy != null) {
            LoginUser user = SecurityUtils.getLoginUserOrNull();
            BizException.throwForbiddenIf(user == null || !policy.canDeleteDirectly(entity, user),
                    "该业务文件必须通过所属业务模块删除");
        } else {
            checkOrgAccess(entity, "无权删除其他组织的文件");
        }
        return deleteEntity(entity);
    }

    /**
     * 调用方已经完成业务资源级授权时使用的内部删除入口。
     * <p>例如培养联系人可能是 SELF 数据范围，无法通过通用文件组织权限，但其与某个申请人
     * 存在精确 trainerIds 关系。材料服务完成该关系校验后，可以删除该材料自己的文件。</p>
     * <p>该方法没有 Controller 映射，禁止在未完成上层资源授权时直接调用。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteAfterBusinessAuthorization(Long fileId) {
        BizException.throwIf(fileId == null, "文件ID不能为空");
        SysFile entity = fileMapper.selectById(fileId);
        if (entity == null) {
            return false;
        }
        return deleteEntity(entity);
    }

    private boolean deleteEntity(SysFile entity) {
        fileStorage.delete(entity.getFilePath());
        boolean success = fileMapper.deleteById(entity.getFileId()) > 0;
        if (success) {
            log.info("文件已删除：fileId={} path={}", entity.getFileId(), entity.getFilePath());
        }
        return success;
    }

    // ==================== 私有方法 ====================

    /**
     * 按 ID 取文件并做越权校验，供详情 / 下载使用。
     *
     * @param fileId 文件ID
     * @return 文件实体
     */
    private SysFile requireReadable(Long fileId) {
        SysFile entity = fileMapper.selectById(fileId);
        BizException.throwIf(entity == null, "文件不存在");
        checkReadAccess(entity, "无权访问该文件");
        return entity;
    }

    /**
     * 统一读取权限：业务文件优先走资源级策略，未命中策略的普通文件退回组织数据范围。
     */
    private void checkReadAccess(SysFile entity, String message) {
        SysFileAccessPolicy policy = findAccessPolicy(entity);
        if (policy != null) {
            LoginUser user = SecurityUtils.getLoginUserOrNull();
            BizException.throwForbiddenIf(user == null || !policy.canRead(entity, user), message);
            return;
        }
        checkOrgAccess(entity, message);
    }

    /** 查找负责当前文件的业务策略；同一文件最多应命中一个策略。 */
    private SysFileAccessPolicy findAccessPolicy(SysFile entity) {
        if (entity == null || accessPolicies == null || accessPolicies.isEmpty()) {
            return null;
        }
        return accessPolicies.stream()
                .filter(policy -> policy.supports(entity))
                .findFirst()
                .orElse(null);
    }

    /**
     * 文件归属组织越权校验。
     * <p>{@code org_id} 为空的历史/内部文件不做限制 —— 与 {@code delete} 口径一致；
     * 正常经 HTTP 接口上传的文件都会带上上传人所在组织。</p>
     *
     * @param entity  文件实体
     * @param message 拒绝时的提示语
     */
    private void checkOrgAccess(SysFile entity, String message) {
        if (entity.getOrgId() != null) {
            BizException.throwForbiddenIf(!DataScopeHelper.canAccessOrg(entity.getOrgId()), message);
        }
    }

    /** 按实体读取文件内容 */
    private FileContent readEntity(SysFile entity) {
        byte[] bytes = fileStorage.read(entity.getFilePath());
        String contentType = StringUtils.hasText(entity.getContentType())
                ? entity.getContentType() : detectContentType(entity.getFileName());
        return new FileContent(bytes, entity.getFileName(), contentType, bytes.length);
    }

    /** 拼接并校验存储相对路径，额外做一层路径穿越防护 */
    private String buildRelativePath(String bizType, String date, String fileName) {
        BizException.throwIf(!StringUtils.hasText(bizType) || !DIR_SEGMENT.matcher(bizType).matches(), "非法的业务类型");
        BizException.throwIf(!StringUtils.hasText(date) || !DATE_SEGMENT.matcher(date).matches(), "非法的文件日期");
        BizException.throwIf(!StringUtils.hasText(fileName) || !FILE_SEGMENT.matcher(fileName).matches(), "非法的文件名");
        return bizType + "/" + date + "/" + fileName;
    }

    /** 业务类型归一化：只保留字母数字下划线短横线 */
    private String normalizeBizType(String bizType) {
        if (!StringUtils.hasText(bizType)) {
            return "common";
        }
        String safe = bizType.replaceAll("[^a-zA-Z0-9_-]", "");
        return safe.isEmpty() ? "common" : safe;
    }

    /** MIME 类型：优先取上传请求中的声明，缺失时按扩展名推断 */
    private String resolveContentType(String contentType, String fileName) {
        if (StringUtils.hasText(contentType)) {
            return contentType;
        }
        return detectContentType(fileName);
    }

    /** 按文件名推断 MIME 类型 */
    private String detectContentType(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        return MediaTypeFactory.getMediaType(fileName)
                .map(MediaType::toString)
                .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    /** 取扩展名（小写，不含点） */
    private String extName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return null;
        }
        int index = fileName.lastIndexOf('.');
        return index < 0 || index == fileName.length() - 1
                ? null : fileName.substring(index + 1).toLowerCase();
    }

    /** 补全组织、上传人等上下文信息，未登录（内部调用）时留空 */
    private void applyUploadContext(SysFile entity) {
        LoginUser user = SecurityUtils.getLoginUserOrNull();
        if (user != null) {
            entity.setOrgId(user.getOrgId());
            entity.setUploadBy(user.getUserId());
        }
        if (!StringUtils.hasText(entity.getStorageType())) {
            entity.setStorageType(fileProperties.getStorage());
        }
    }

    /**
     * 文件内容载体，供 Controller 输出流使用。
     *
     * @param bytes       文件字节
     * @param fileName    原始文件名
     * @param contentType MIME 类型
     * @param size        字节数
     */
    public record FileContent(byte[] bytes, String fileName, String contentType, long size) {
    }
}
