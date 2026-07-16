package org.cloudback.order.mq;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.common.result.R;
import org.cloudback.order.feign.ProductFeignClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockRestoreConsumer {

    private final ProductFeignClient productFeignClient;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${cloud.internal.token}")
    private String internalToken;

    @KafkaListener(topics = SystemConstants.KAFKA_TOPIC_STOCK_RESTORE, groupId = "order-stock-restore-group")
    public void onStockRestore(String message) {
        Long productId;
        Integer quantity;
        String idempotencyKey;
        try {
            JSONObject msg = JSON.parseObject(message);
            productId = msg.getLong("productId");
            quantity = msg.getInteger("quantity");
            idempotencyKey = msg.getString("idempotencyKey");
        } catch (JSONException e) {
            log.error("Invalid stock restore message: {}", message, e);
            return;
        }

        if (productId == null || quantity == null || quantity <= 0 || idempotencyKey == null || idempotencyKey.isBlank()) {
            log.error("Stock restore message missing fields: {}", message);
            return;
        }

        String lockKey = SystemConstants.REDIS_KEY_PREFIX + "stock:restore:" + idempotencyKey;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "PROCESSING", 5, TimeUnit.MINUTES);
        if (locked == null || !locked) {
            Object state = redisTemplate.opsForValue().get(lockKey);
            if ("DONE".equals(state)) {
                log.info("Stock restore already processed: key={}", idempotencyKey);
                return;
            }
            throw new IllegalStateException("Stock restore is being processed: " + idempotencyKey);
        }

        try {
            R<String> result = productFeignClient.restoreStock(productId, quantity, internalToken);
            if (result.getCode() != 200) {
                throw new IllegalStateException("Restore stock failed: " + result.getMessage());
            }
            redisTemplate.opsForValue().set(lockKey, "DONE", 7, TimeUnit.DAYS);
            log.info("Stock restore processed: key={}, productId={}, quantity={}", idempotencyKey, productId, quantity);
        } catch (Exception e) {
            redisTemplate.delete(lockKey);
            log.error("Stock restore failed and will be retried: key={}", idempotencyKey, e);
            throw e;
        }
    }
}
