package com.hparty.develop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hparty.develop.domain.entity.DevStepRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 步骤办理记录 Mapper
 */
@Mapper
public interface DevStepRecordMapper extends BaseMapper<DevStepRecord> {
}
