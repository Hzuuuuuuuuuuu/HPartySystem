package com.hparty.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hparty.system.domain.entity.SysLoginLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录日志 Mapper
 */
@Mapper
public interface SysLoginLogMapper extends BaseMapper<SysLoginLog> {
}
