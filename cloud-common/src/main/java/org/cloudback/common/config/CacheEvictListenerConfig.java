package org.cloudback.common.config;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.constant.SystemConstants;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

/**
 * 两级缓存跨实例 L1 失效监听：订阅失效广播频道，
 * 收到消息后失效本实例所有 Caffeine L1 缓存中对应的 key。
 * 通过 ObjectProvider 惰性获取 Cache Bean，避免与 CaffeineConfig 产生循环依赖。
 *
 * @author CloudBack
 */
@Slf4j
@Configuration
@ConditionalOnClass(RedisConnectionFactory.class)
public class CacheEvictListenerConfig {

    @Bean
    public RedisMessageListenerContainer cacheEvictListenerContainer(
            RedisConnectionFactory connectionFactory,
            ObjectProvider<Cache<String, Object>> caffeineCaches) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> {
            String key = new String(message.getBody(), StandardCharsets.UTF_8);
            caffeineCaches.forEach(cache -> cache.invalidate(key));
            log.debug("L1 invalidated by broadcast: {}", key);
        }, new ChannelTopic(SystemConstants.CACHE_EVICT_CHANNEL));
        return container;
    }
}
