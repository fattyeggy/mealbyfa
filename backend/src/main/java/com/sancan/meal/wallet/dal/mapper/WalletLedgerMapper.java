package com.sancan.meal.wallet.dal.mapper;

import com.sancan.meal.wallet.dal.dataobject.WalletLedgerDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 账本 Mapper。刻意不继承 {@code BaseMapper}：
 * 该接口只暴露插入与查询，没有 update / delete，账本无法被业务代码改写或删除。
 */
@Mapper
public interface WalletLedgerMapper {

    /** 仅转移归属（认领后台建档的手机号），金额与流水内容不变。 */
    @org.apache.ibatis.annotations.Update("""
            UPDATE meal_wallet_ledger
               SET user_id = #{toUserId}, account_id = #{toAccountId}
             WHERE user_id = #{fromUserId}
            """)
    int transferOwner(@org.apache.ibatis.annotations.Param("fromUserId") Long fromUserId,
                      @org.apache.ibatis.annotations.Param("toUserId") Long toUserId,
                      @org.apache.ibatis.annotations.Param("toAccountId") Long toAccountId);


    @Insert("""
            INSERT INTO meal_wallet_ledger
            (account_id, user_id, biz_type, direction, source, amount, balance_before, balance_after,
             ref_type, ref_id, reverse_of_id, reason, operator_type, operator_id, create_time)
            VALUES
            (#{accountId}, #{userId}, #{bizType}, #{direction}, #{source}, #{amount}, #{balanceBefore},
             #{balanceAfter}, #{refType}, #{refId}, #{reverseOfId}, #{reason}, #{operatorType},
             #{operatorId}, CURRENT_TIMESTAMP)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WalletLedgerDO ledger);

    @Select("SELECT * FROM meal_wallet_ledger WHERE id = #{id}")
    WalletLedgerDO selectById(@Param("id") Long id);

    @Select("""
            SELECT * FROM meal_wallet_ledger
            WHERE user_id = #{userId}
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<WalletLedgerDO> selectPage(@Param("userId") Long userId,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM meal_wallet_ledger WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT * FROM meal_wallet_ledger
            WHERE ref_type = #{refType} AND ref_id = #{refId} AND biz_type = #{bizType}
            ORDER BY id ASC
            """)
    List<WalletLedgerDO> selectByRef(@Param("bizType") String bizType,
                                     @Param("refType") String refType,
                                     @Param("refId") Long refId);
}
