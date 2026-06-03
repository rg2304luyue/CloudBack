package org.cloudback.payment.config;

import org.cloudback.common.mapper.OutboxMessageMapper;
import org.cloudback.common.service.OutboxService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付模块 Outbox 配置，显式声明 OutboxService Bean（由本模块扫描 common mapper）。
 *
 * @author CloudBack
 * @since 2026-06-03
 */
@Configuration
public class OutboxConfig {

    @Bean
    public OutboxService outboxService(OutboxMessageMapper mapper) {
        return new OutboxService(mapper);
    }
}
