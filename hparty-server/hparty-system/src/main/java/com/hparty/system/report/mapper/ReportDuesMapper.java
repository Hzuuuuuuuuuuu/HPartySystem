package com.hparty.system.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hparty.system.report.entity.ReportDuesRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 党费收缴台账导出 Mapper（{@code party_dues_record} 最小投影，只读）。
 */
@Mapper
public interface ReportDuesMapper extends BaseMapper<ReportDuesRecord> {
}
