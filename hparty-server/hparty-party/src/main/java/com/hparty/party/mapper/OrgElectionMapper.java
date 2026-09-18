package com.hparty.party.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hparty.party.domain.entity.OrgElection;
import org.apache.ibatis.annotations.Mapper;

/**
 * 党组织换届选举 Mapper
 */
@Mapper
public interface OrgElectionMapper extends BaseMapper<OrgElection> {
}
