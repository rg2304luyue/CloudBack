package org.cloudback.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 跨域配置，允许前端（任意来源）跨域访问 Gateway。
 * Gateway 基于 WebFlux，需使用 CorsWebFilter 而非传统 CorsFilter。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Configuration
public class CorsConfig {

    /**
     * 注册 CorsWebFilter。
     * 开发环境放行所有来源；生产环境应改为配置具体的允许域名列表。
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("*"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
