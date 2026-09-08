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
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class StockRestoreConsumer {

    private final ProductFeignClient productFeignClient;

    @Value("${cloud.internal.token}")
    private String internalToken;

    @KafkaListener(topics = SystemConstants.KAFKA_TOPIC_STOCK_RESTORE, groupId = "order-stock-restore-group")
    public void onStockRestore(String message) {
        Long productId;
        Integer quantity;
        String idempotencyKey;
        String orderNo;
        try {
            JSONObject msg = JSON.parseObject(message);
            if (msg == null) {
                log.error("Invalid null stock restore message");
                return;
            }
            productId = msg.getLong("productId");
            quantity = msg.getInteger("quantity");
            idempotencyKey = msg.getString("idempotencyKey");
            orderNo = msg.getString("orderNo");
        } catch (JSONException e) {
            log.error("Invalid stock restore message: {}", message, e);
            return;
        }

        if (productId == null || quantity == null || quantity <= 0 || orderNo == null || orderNo.isBlank()
                || !(orderNo + ":" + productId).equals(idempotencyKey)) {
            log.error("Stock restore message missing fields: {}", message);
            return;
        }

        try {
            // Durable deduplication is committed with inventory by the product service.
            R<String> result = productFeignClient.restoreStock(productId, quantity, orderNo, internalToken);
            if (result == null || result.getCode() != 200) {
                throw new IllegalStateException("Restore stock failed");
            }
            log.info("Stock restore processed: key={}, productId={}, quantity={}", idempotencyKey, productId, quantity);
        } catch (Exception e) {
            log.error("Stock restore failed and will be retried: key={}", idempotencyKey, e);
            throw e;
        }
    }
}
