package org.cloudback.common.config;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "meili", name = "host")
public class MeilisearchConfig {

    @Bean
    public Client meilisearchClient(
            @Value("${meili.host}") String host,
            @Value("${meili.port}") int port,
            @Value("${meili.master-key}") String masterKey) {
        Config config = new Config(
                "http://" + host + ":" + port,
                masterKey
        );
        Client client = new Client(config);
        log.info("Meilisearch 客户端初始化完成: {}:{}", host, port);
        return client;
    }
}