package com.hparty.party.mapper;

import com.hparty.party.domain.entity.PartyFile;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

/**
 * 党务域写入 {@code sys_file} 的通道。
 *
 * <p>{@code hparty-party} 不依赖 {@code hparty-system}，无法直接使用
 * {@code SysFileService.upload(...)}，因此用注解 SQL 直接落库 ——
 * 这与 {@link PartyLookupMapper} 读 {@code sys_dept} / {@code party_person} 的做法一致，
 * 是本项目跨域访问既有表的方式。</p>
 *
 * <p>注意：这里只负责「登记一行文件记录」，大小限制、扩展名白名单、路径穿越防护
 * 全部由 {@code FileStorage} 实现（{@code LocalFileStorage}）负责，调用前必须先落盘成功。</p>
 */
@Mapper
public interface PartyFileMapper {

    /**
     * 登记一条文件记录，回填自增主键。
     *
     * @param file 文件信息，fileId 由数据库生成后回填
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO sys_file (file_name, file_path, file_url, file_suffix, file_size,
                                  content_type, storage_type, biz_type, biz_id, org_id, upload_by,
                                  del_flag, create_time)
            VALUES (#{fileName}, #{filePath}, #{fileUrl}, #{fileSuffix}, #{fileSize},
                    #{contentType}, #{storageType}, #{bizType}, #{bizId}, #{orgId}, #{uploadBy},
                    0, NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "fileId")
    int insertFile(PartyFile file);
}
