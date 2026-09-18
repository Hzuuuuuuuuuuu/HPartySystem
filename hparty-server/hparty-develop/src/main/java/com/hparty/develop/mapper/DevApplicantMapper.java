package com.hparty.develop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hparty.develop.domain.entity.DevApplicant;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 发展党员申请人实例 Mapper
 */
@Mapper
public interface DevApplicantMapper extends BaseMapper<DevApplicant> {

    /**
     * 查某人的全部发展党员实例，**含逻辑删除的行**。
     *
     * <p>MyBatis-Plus 的 {@code @TableLogic} 会给普通查询自动追加
     * {@code del_flag = 0}，因此看不到已删除的行。但唯一索引
     * {@code uk_applicant_person(person_id)} 建在 person_id 单列上，
     * 被逻辑删除的行**仍然占用索引** —— 不查出来就会在插入时撞唯一键。</p>
     *
     * @return 每项含 applicant_id 与 del_flag
     */
    @Select("SELECT applicant_id AS applicantId, del_flag AS delFlag "
            + "FROM dev_applicant WHERE person_id = #{personId}")
    List<java.util.Map<String, Object>> selectAllByPersonId(@Param("personId") Long personId);

    /** 物理删除发展党员实例（绕开逻辑删除），用于清理占用唯一键的残留行 */
    @Delete("DELETE FROM dev_applicant WHERE applicant_id = #{applicantId}")
    int hardDeleteById(@Param("applicantId") Long applicantId);

    /** 物理删除该实例的步骤办理记录 */
    @Delete("DELETE FROM dev_step_record WHERE applicant_id = #{applicantId}")
    int hardDeleteRecords(@Param("applicantId") Long applicantId);
}
