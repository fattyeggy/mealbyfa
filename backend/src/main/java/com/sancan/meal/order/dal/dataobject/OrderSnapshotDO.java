package com.sancan.meal.order.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 锁单快照：只创建一次，之后任何菜单、菜谱、价格调整都不影响历史订单。 */
@Data
@TableName("meal_order_snapshot")
public class OrderSnapshotDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long menuId;
    private String menuVersion;
    private BigDecimal totalPrice;
    private String payload;
    private LocalDateTime createTime;
}
