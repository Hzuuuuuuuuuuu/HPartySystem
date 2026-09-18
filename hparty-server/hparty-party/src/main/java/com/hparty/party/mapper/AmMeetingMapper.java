package com.hparty.party.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hparty.party.domain.entity.AmMeeting;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会议（三会一课/主题党日/组织生活会） Mapper
 */
@Mapper
public interface AmMeetingMapper extends BaseMapper<AmMeeting> {
}
