package org.cloudback.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 订单服务启动类。
 * 通过 Feign 调用 cart、product、user 服务，通过 Kafka 与 payment 服务异步通信。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@EnableFeignClients
@EnableDiscoveryClient
@SpringBootApplication
@MapperScan("org.cloudback.order.mapper")
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
