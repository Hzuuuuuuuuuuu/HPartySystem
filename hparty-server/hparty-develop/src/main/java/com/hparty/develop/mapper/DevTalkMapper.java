package com.hparty.develop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hparty.develop.domain.entity.DevTalk;
import org.apache.ibatis.annotations.Mapper;

/**
 * 谈话记录 Mapper
 */
@Mapper
public interface DevTalkMapper extends BaseMapper<DevTalk> {
}
