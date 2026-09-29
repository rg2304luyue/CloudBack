package org.cloudback.order.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka 消费异常处理：固定间隔重试 3 次后投递死信主题（{topic}.DLQ），防止消息静默丢失。
 * Spring Boot 会自动将 CommonErrorHandler Bean 装配进监听器容器工厂。
 */
@Slf4j
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> {
                    log.error("Kafka 消费多次失败，转入死信: topic={}, partition={}, offset={}, key={}",
                            record.topic(), record.partition(), record.offset(), record.key(), ex);
                    return new TopicPartition(record.topic() + ".DLQ", record.partition());
                });
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    }
}
