package com.sancan.meal.order.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sancan.meal.common.po.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订单。{@code activeKey} 为 1 时表示有效订单，取消后置空，
 * 配合唯一索引 (user_id, service_date, active_key) 实现「用户 + 服务日唯一有效订单」。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("meal_order")
public class OrderDO extends BaseDO {

    private String orderNo;
    private Long userId;
    private LocalDate serviceDate;
    private Long planId;
    private String status;
    private BigDecimal originalAmount;
    /** 下单时绑定的定价版本，后台改价不影响本单。 */
    private Long pricingVersionId;
    private String pricingVersionNo;
    /** 下单时绑定的菜单来源；锁单快照只读这里。 */
    private Long sourceMenuId;
    private String sourceMenuVersion;
    /** 取整前的未取整日价（审计用）。 */
    private BigDecimal rawPrice;
    private BigDecimal swapDifference;
    private Long couponId;
    private BigDecimal couponAmount;
    private BigDecimal balanceAmount;
    private BigDecimal creditAmount;
    private BigDecimal cashAmount;
    private BigDecimal payAmount;
    private String contactName;
    private String contactPhone;
    private String address;
    private Integer activeKey;
    /** 周订单组；单日订单为空。 */
    private Long groupId;
    private LocalDateTime lockTime;
    private String lockFailReason;
    private LocalDateTime cancelTime;
    private String cancelReason;
    private LocalDateTime refundTime;
    private String remark;
}
