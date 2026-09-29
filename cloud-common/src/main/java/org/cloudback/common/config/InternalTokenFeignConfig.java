package org.cloudback.common.config;

import feign.RequestInterceptor;
import org.cloudback.common.constant.SystemConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 内部 Token 拦截器：为所有服务间 Feign 调用注入 X-Internal-Token 头，
 * 与 InternalAuthFilter 配合，保证下游服务只信任来自网关/内部服务的请求。
 *
 * @author CloudBack
 */
@Configuration
@ConditionalOnClass(RequestInterceptor.class)
public class InternalTokenFeignConfig {

    @Value("${cloud.internal.token:}")
    private String internalToken;

    @Bean
    public RequestInterceptor internalTokenRequestInterceptor() {
        return template -> {
            if (internalToken == null || internalToken.isBlank()) {
                return;
            }
            // 部分调用方已显式传入该头，不覆盖
            if (!template.headers().containsKey(SystemConstants.INTERNAL_TOKEN_HEADER)) {
                template.header(SystemConstants.INTERNAL_TOKEN_HEADER, internalToken);
            }
        };
    }
}
