package com.hparty.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hparty.system.domain.entity.PartyMemberProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 党员扩展信息 Mapper
 */
@Mapper
public interface PartyMemberProfileMapper extends BaseMapper<PartyMemberProfile> {
}
