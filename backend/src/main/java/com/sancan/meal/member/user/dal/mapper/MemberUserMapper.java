package com.sancan.meal.member.user.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sancan.meal.member.user.dal.dataobject.MemberUserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MemberUserMapper extends BaseMapper<MemberUserDO> {

    @Select("SELECT * FROM member_user WHERE openid = #{openid} AND deleted = 0 LIMIT 1")
    MemberUserDO selectByOpenid(@Param("openid") String openid);

    @org.apache.ibatis.annotations.Select("SELECT * FROM member_user WHERE mobile = #{mobile} AND deleted = 0 LIMIT 1")
    MemberUserDO selectByMobile(@Param("mobile") String mobile);
}
