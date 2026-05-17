package org.cloudback.common.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置，注册核心拦截器。
 * 新版（3.5.10+）分页功能已内置，无需单独添加 PaginationInnerInterceptor。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Configuration
public class MyBatisPlusConfig {

    /** 注册 MybatisPlusInterceptor，支持分页等能力 */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        return new MybatisPlusInterceptor();
    }
}
