package com.sky.task;

import com.sky.constant.MessageConstant;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class OrderTask {
    /**
     * 订单超时时间限制（分钟）
     */
    public static final int ORDER_TIME_OUT_MINUTES = 15;
    @Autowired
    private OrderMapper orderMapper;
    /**
     * 处理超时订单
     */
    @Scheduled(cron = "0 * * * * ?")//每分钟执行一次
    public void processTimeoutOrder() {
        log.info("处理超时订单,当前时间：{}", LocalDateTime.now());
        //计算超时订单对应的下单时间
        LocalDateTime orderTime = LocalDateTime.now().minusMinutes(ORDER_TIME_OUT_MINUTES);
        //查询超时订单
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.PENDING_PAYMENT, orderTime);
        if(!ordersList.isEmpty()){
            for (Orders orders : ordersList) {
                orders.setStatus(Orders.CANCELLED);
                orders.setCancelReason(MessageConstant.ORDER_TIMEOUT);
                orders.setCancelTime(LocalDateTime.now());
                orderMapper.update(orders);
            }
        }
    }

    /**
     * 处理一直处于派送中的订单
     */
    @Scheduled(cron = "0 0 1 * * ?")//每天凌晨1点执行
    public void processDeliveryOrder(){
        log.info("处理一直处于派送中的订单,当前时间：{}", LocalDateTime.now());
        //计算超时订单对应的下单时间(即前一天的订单)
        LocalDateTime orderTime = LocalDateTime.now().minusHours(1);
        //查询超时订单
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.DELIVERY_IN_PROGRESS, orderTime);
        if(!ordersList.isEmpty()){
            for (Orders orders : ordersList) {
                orders.setStatus(Orders.COMPLETED);
                orderMapper.update(orders);
            }
        }

    }
}
