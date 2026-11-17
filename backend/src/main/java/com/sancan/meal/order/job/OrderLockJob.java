package com.sancan.meal.order.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sancan.meal.order.dal.dataobject.OrderDO;
import com.sancan.meal.order.dal.mapper.OrderMapper;
import com.sancan.meal.order.enums.OrderStatus;
import com.sancan.meal.order.service.OrderLockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 锁单批处理。每张订单单独一个事务，单笔失败不影响其他订单。
 *
 * <p>单独成 Bean 而不是放在 {@link OrderLockService} 里循环调用自己，
 * 是为了让 {@code lockOne} 走 Spring 代理，事务注解才生效。
 */
@Component
public class OrderLockJob {

    private static final Logger log = LoggerFactory.getLogger(OrderLockJob.class);

    private final OrderMapper orderMapper;
    private final OrderLockService orderLockService;

    public OrderLockJob(OrderMapper orderMapper, OrderLockService orderLockService) {
        this.orderMapper = orderMapper;
        this.orderLockService = orderLockService;
    }

    /** 执行结果汇总。 */
    public record LockRunResult(LocalDate serviceDate, int total, int locked, int failed,
                                int skipped, List<String> messages) {
    }

    public LockRunResult run(LocalDate serviceDate) {
        List<OrderDO> orders = orderMapper.selectList(new LambdaQueryWrapper<OrderDO>()
                .eq(OrderDO::getServiceDate, serviceDate)
                .eq(OrderDO::getStatus, OrderStatus.PENDING_LOCK.name())
                .orderByAsc(OrderDO::getId));
        int locked = 0;
        int failed = 0;
        int skipped = 0;
        List<String> messages = new ArrayList<>();
        for (OrderDO order : orders) {
            try {
                OrderLockService.Outcome outcome = orderLockService.lockOne(order.getId());
                switch (outcome) {
                    case LOCKED -> locked++;
                    case FAILED_INSUFFICIENT -> {
                        failed++;
                        messages.add(order.getOrderNo() + " 余额不足");
                    }
                    default -> skipped++;
                }
            } catch (Exception ex) {
                failed++;
                messages.add(order.getOrderNo() + " 锁单异常：" + ex.getMessage());
                log.error("锁单异常 orderId={}", order.getId(), ex);
            }
        }
        log.info("锁单任务完成 服务日={} 总数={} 成功={} 失败={} 跳过={}",
                serviceDate, orders.size(), locked, failed, skipped);
        return new LockRunResult(serviceDate, orders.size(), locked, failed, skipped, messages);
    }
}
