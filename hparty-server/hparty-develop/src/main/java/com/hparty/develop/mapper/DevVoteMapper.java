package com.hparty.develop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hparty.develop.domain.entity.DevVote;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支部大会表决记录 Mapper
 */
@Mapper
public interface DevVoteMapper extends BaseMapper<DevVote> {
}
