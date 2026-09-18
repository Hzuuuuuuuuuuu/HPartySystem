package com.hparty.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hparty.system.domain.entity.PartyPosition;
import org.apache.ibatis.annotations.Mapper;

/**
 * 党内职务任职记录 Mapper
 */
@Mapper
public interface PartyPositionMapper extends BaseMapper<PartyPosition> {
}
