package com.hparty.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hparty.system.domain.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;

/**
 * 菜单权限 Mapper
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {
}
