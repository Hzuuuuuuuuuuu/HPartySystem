package com.hparty.party.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hparty.party.domain.entity.AmAttendee;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会议参会人员 Mapper
 */
@Mapper
public interface AmAttendeeMapper extends BaseMapper<AmAttendee> {
}
