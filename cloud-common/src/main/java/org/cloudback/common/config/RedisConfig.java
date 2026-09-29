package org.cloudback.common.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.filter.Filter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.nio.charset.StandardCharsets;

@Configuration
@ConditionalOnClass(RedisConnectionFactory.class)
public class RedisConfig {

    @Bean
    @Primary
    public RedisTemplate<String, Object> customRedisTemplate(RedisConnectionFactory factory) {
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

    private static class Fastjson2Serializer implements RedisSerializer<Object> {

        /** AutoType 白名单：仅允许反序列化项目自身类与常见 JDK 类型，防止反序列化漏洞 */
        private static final Filter AUTO_TYPE_FILTER = JSONReader.autoTypeFilter(
                "org.cloudback.", "java.util.", "java.time.", "java.math.", "java.lang.");

        @Override
        public byte[] serialize(Object obj) throws SerializationException {
            if (obj == null) return new byte[0];
            return JSON.toJSONString(obj, JSONWriter.Feature.WriteClassName)
                    .getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Object deserialize(byte[] bytes) throws SerializationException {
            if (bytes == null || bytes.length == 0) return null;
            return JSON.parseObject(new String(bytes, StandardCharsets.UTF_8), Object.class,
                    AUTO_TYPE_FILTER, JSONReader.Feature.SupportAutoType);
        }
    }
}
