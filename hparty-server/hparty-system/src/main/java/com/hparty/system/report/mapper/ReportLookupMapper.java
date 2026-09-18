package com.hparty.system.report.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 导出用到的跨域补名字段查询。
 *
 * <p>导出四个报表时要给每一行补上「组织名称 / 人员姓名 / 步骤名称」这类
 * 关联域字段，而 {@code hparty-system} 不依赖 {@code hparty-party} /
 * {@code hparty-develop}，因此统一用注解 SQL 直接查表（与
 * {@code hparty-party} 的 {@code PartyLookupMapper} 同一思路）。</p>
 *
 * <p>都是**按批次 IN 查询**，不会产生 N+1。</p>
 */
@Mapper
public interface ReportLookupMapper {

    /** 批量：组织 ID → 组织名称 */
    @Select("""
            <script>
            SELECT org_id, org_name FROM sys_dept
            WHERE del_flag = 0 AND org_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<Map<String, Object>> selectOrgNames(@Param("ids") List<Long> ids);

    /** 批量：人员 ID → 姓名 */
    @Select("""
            <script>
            SELECT person_id, name FROM party_person
            WHERE del_flag = 0 AND person_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<Map<String, Object>> selectPersonNames(@Param("ids") List<Long> ids);

    /** 步骤编码 → 步骤名称 */
    @Select("SELECT step_code, step_name FROM dev_step")
    List<Map<String, Object>> selectStepNames();

    /** 阶段编码 → 阶段名称 */
    @Select("SELECT stage_code, stage_name FROM dev_stage")
    List<Map<String, Object>> selectStageNames();

    /** 批量：评议批次 ID → 标题/年度 */
    @Select("""
            <script>
            SELECT review_id, title, review_year FROM party_review
            WHERE del_flag = 0 AND review_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<Map<String, Object>> selectReviewTitles(@Param("ids") List<Long> ids);

    /** 按评议年度取批次 ID（用于「导出某年度评议结果」） */
    @Select("SELECT review_id FROM party_review WHERE del_flag = 0 AND review_year = #{year}")
    List<Long> selectReviewIdsByYear(@Param("year") Integer year);

    /** 姓名模糊匹配的人员 ID（发展党员进度表按姓名筛选用） */
    @Select("SELECT person_id FROM party_person WHERE del_flag = 0 AND name LIKE CONCAT('%', #{name}, '%')")
    List<Long> selectPersonIdsByName(@Param("name") String name);
}
