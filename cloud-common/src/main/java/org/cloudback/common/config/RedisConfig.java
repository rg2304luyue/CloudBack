package org.cloudback.common.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.nio.charset.StandardCharsets;

/**
 * Redis 配置，使用 Fastjson2 作为序列化器。
 * Key 使用 String 序列化，Value 使用 Fastjson2 序列化。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Configuration
public class RedisConfig {

    /** 配置 RedisTemplate，String Key + Fastjson2 Value */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        Fastjson2Serializer serializer = new Fastjson2Serializer();

        template.setKeySerializer(RedisSerializer.string());
        template.setHashKeySerializer(RedisSerializer.string());
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }

    /** Fastjson2 序列化器，支持带类名序列化以保留泛型信息 */
    private static class Fastjson2Serializer implements RedisSerializer<Object> {
        @Override
        public byte[] serialize(Object obj) throws SerializationException {
            if (obj == null) return new byte[0];
            return JSON.toJSONBytes(obj, JSONWriter.Feature.WriteClassName);
        }

        @Override
        public Object deserialize(byte[] bytes) throws SerializationException {
            if (bytes == null || bytes.length == 0) return null;
            return JSON.parseObject(bytes, Object.class, JSONReader.Feature.SupportAutoType);
        }
    }
}
