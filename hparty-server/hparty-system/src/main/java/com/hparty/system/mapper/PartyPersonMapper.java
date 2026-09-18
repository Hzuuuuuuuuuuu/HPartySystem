package com.hparty.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hparty.system.domain.entity.PartyPerson;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 人员统一档案 Mapper
 */
@Mapper
public interface PartyPersonMapper extends BaseMapper<PartyPerson> {

    /**
     * 统计该人员是否存在「进行中」的发展党员流程。
     * <p>发展党员模块的表 {@code dev_applicant} 不属于本模块，无法引用其实体，
     * 因此直接用原生 SQL 计数；逻辑删除条件需手写（MyBatis-Plus 逻辑删除只作用于内置方法）。</p>
     *
     * @param personId 人员ID
     * @return 进行中的流程条数
     */
    @Select("SELECT COUNT(*) FROM dev_applicant WHERE person_id = #{personId} AND status = 1 AND del_flag = 0")
    int countRunningFlow(@Param("personId") Long personId);
}
