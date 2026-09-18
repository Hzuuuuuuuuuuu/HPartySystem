package com.hparty.system.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hparty.system.report.entity.ReportMeeting;
import org.apache.ibatis.annotations.Mapper;

/**
 * 三会一课开展情况导出 Mapper（{@code am_meeting} 最小投影，只读）。
 */
@Mapper
public interface ReportMeetingMapper extends BaseMapper<ReportMeeting> {
}
