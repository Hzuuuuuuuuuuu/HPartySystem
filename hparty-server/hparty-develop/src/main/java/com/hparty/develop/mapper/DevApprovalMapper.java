package com.hparty.develop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hparty.develop.domain.entity.DevApproval;
import org.apache.ibatis.annotations.Mapper;

/**
 * 上级审批备案记录 Mapper
 */
@Mapper
public interface DevApprovalMapper extends BaseMapper<DevApproval> {
}
