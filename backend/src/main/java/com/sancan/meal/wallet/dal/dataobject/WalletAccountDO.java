package com.sancan.meal.wallet.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 账户：总余额由赠送 credit 与真实现金两部分构成。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_wallet_account")
public class WalletAccountDO extends BaseDO {

    private Long userId;
    private BigDecimal balance;
    private BigDecimal creditBalance;
    private BigDecimal cashBalance;
    /** 乐观锁版本号，每次余额变动 +1。 */
    private Integer version;
}
