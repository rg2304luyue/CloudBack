package org.cloudback.common.config;

import jakarta.annotation.PostConstruct;
import org.cloudback.common.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtSecretConfig {

    @Value("${JWT_SECRET}")
    private String jwtSecret;

    @PostConstruct
    public void init() {
        JwtUtil.setSecret(jwtSecret);
    }
}
