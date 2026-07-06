package com.agent.user.mapper;

import com.agent.user.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT id, username, password, status, deleted FROM sys_user WHERE username = #{username} AND deleted = 0")
    SysUser selectByUsername(String username);
}
