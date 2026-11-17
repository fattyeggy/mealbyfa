package com.sancan.meal.system.user.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sancan.meal.system.user.dal.dataobject.AdminUserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUserDO> {

    @Select("SELECT * FROM sys_user WHERE username = #{username} AND deleted = 0 LIMIT 1")
    AdminUserDO selectByUsername(@Param("username") String username);
}
