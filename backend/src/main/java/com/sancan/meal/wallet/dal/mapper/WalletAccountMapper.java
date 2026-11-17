package com.sancan.meal.wallet.dal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sancan.meal.wallet.dal.dataobject.WalletAccountDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WalletAccountMapper extends BaseMapper<WalletAccountDO> {

    @Select("SELECT * FROM meal_wallet_account WHERE user_id = #{userId} AND deleted = 0 LIMIT 1")
    WalletAccountDO selectByUserId(@Param("userId") Long userId);
}
