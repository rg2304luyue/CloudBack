package org.cloudback.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 用户服务启动类。
 * 提供用户信息查询/修改、收货地址增删改查功能。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@MapperScan({"org.cloudback.common.mapper", "org.cloudback.user.mapper"})
@EnableDiscoveryClient
@SpringBootApplication
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
