package org.cloudback.cart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 购物车服务启动类。
 * 购物车数据存储在 Redis Hash 中，通过 Feign 调用商品服务获取商品信息。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@EnableDiscoveryClient
@SpringBootApplication
@EnableFeignClients
public class CartApplication {

    public static void main(String[] args) {
        SpringApplication.run(CartApplication.class, args);
    }
}
