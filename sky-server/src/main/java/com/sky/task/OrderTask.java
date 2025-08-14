package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 自定义定时任务，实现订单状态定时处理
 * */
@Component
@Slf4j
public class OrderTask {

    @Autowired
    OrderMapper orderMapper;

    /**
     * 处理支付超时订单：
     * 将支付超过15分钟的订单状态改为“取消”
     * */
    @Scheduled(cron = "0 * * * * *")
    public void processTimeoutOrder(){
        log.info("处理支付超时订单");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime time = now.plusMinutes(-15);
        // 找出所有15分钟前的待支付订单
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTime(Orders.PENDING_PAYMENT, time);
        if (ordersList != null && ordersList.size() > 0){
            for (Orders order : ordersList){
                order.setStatus(Orders.CANCELLED);
                order.setCancelReason("支付超时，自动取消");
                order.setCancelTime(LocalDateTime.now());
                orderMapper.update(order);
            }
        }
    }

    /**
     * 处理派送超时订单：
     * 每天凌晨一点检查订单状态，把一小时以前下单的所有派送中订单修改为已完成
     * */
    @Scheduled(cron = "0 0 1 * * *")
    public void processDeliveryTimeoutOrder(){
        log.info("处理派送超时订单");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime time = now.plusMinutes(-60);
        // 找出所有15分钟前的待支付订单
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTime(Orders.DELIVERY_IN_PROGRESS, time);
        if (ordersList != null && ordersList.size() > 0){
            for (Orders order : ordersList){
                order.setStatus(Orders.COMPLETED);
                orderMapper.update(order);
            }
        }
    }
}
