package com.hparty.system.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hparty.system.report.entity.ReportReviewDetail;
import org.apache.ibatis.annotations.Mapper;

/**
 * 民主评议结果导出 Mapper（{@code party_review_detail} 最小投影，只读）。
 */
@Mapper
public interface ReportReviewMapper extends BaseMapper<ReportReviewDetail> {
}
