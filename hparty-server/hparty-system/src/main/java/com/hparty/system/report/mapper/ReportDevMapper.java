package com.hparty.system.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hparty.system.report.entity.ReportDevApplicant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 发展党员进度表导出 Mapper（{@code dev_applicant} 最小投影，只读）。
 */
@Mapper
public interface ReportDevMapper extends BaseMapper<ReportDevApplicant> {
}
