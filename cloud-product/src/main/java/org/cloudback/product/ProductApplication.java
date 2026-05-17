package org.cloudback.product;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 商品服务启动类。
 * 提供商品 CRUD、分类管理、库存扣减功能，支持 Feign 远程调用。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@MapperScan("org.cloudback.product.mapper")
@EnableFeignClients
@EnableDiscoveryClient
@SpringBootApplication
public class ProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductApplication.class, args);
    }
}
