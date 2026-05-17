package org.cloudback.order.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign 请求拦截器，将当前 HTTP 请求中的 X-User-Id、X-Username 透传到 Feign 调用。
 * 确保调用 cart、product、user 服务时用户上下文不丢失。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Configuration
public class FeignRequestInterceptor {

    /** 注册 Feign 拦截器，从当前请求上下文提取用户信息并注入 Feign 请求头 */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return (RequestTemplate template) -> {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String userId = request.getHeader("X-User-Id");
                String username = request.getHeader("X-Username");
                if (userId != null) {
                    template.header("X-User-Id", userId);
                }
                if (username != null) {
                    template.header("X-Username", username);
                }
            }
        };
    }
}
