package org.cloudback.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 认证服务启动类。
 * 提供用户注册、登录、JWT 签发功能。
 * @MapperScan 需要扫描 cloud-common 下的 Mapper，因为 UserMapper 不在本模块包路径内。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@MapperScan("org.cloudback.common.mapper")
@EnableDiscoveryClient
@SpringBootApplication
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
