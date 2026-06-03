package org.cloudback.payment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 支付服务启动类。
 * 监听 Kafka order-create 消息，处理支付并回写支付结果。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@MapperScan({"org.cloudback.payment.mapper", "org.cloudback.common.mapper"})
@EnableDiscoveryClient
@EnableScheduling
@SpringBootApplication
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
