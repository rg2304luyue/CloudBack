package org.cloudback.common.constant;

public interface SystemConstants {

    String TOKEN_HEADER = "Authorization";
    String TOKEN_PREFIX = "Bearer ";

    String REDIS_KEY_PREFIX = "cloud:";
    String CART_KEY_PREFIX = REDIS_KEY_PREFIX + "cart:";
    String TOKEN_BLACKLIST_KEY = REDIS_KEY_PREFIX + "token:blacklist:";

    Integer USER_STATUS_NORMAL = 1;
    Integer USER_STATUS_DISABLED = 0;

    Integer ORDER_STATUS_UNPAID = 0;
    Integer ORDER_STATUS_PAID = 1;
    Integer ORDER_STATUS_SHIPPED = 2;
    Integer ORDER_STATUS_COMPLETED = 3;
    Integer ORDER_STATUS_CANCELLED = 4;

    String KAFKA_TOPIC_ORDER_CREATE = "order-create";
    String KAFKA_TOPIC_PAYMENT_RESULT = "payment-result";
    String KAFKA_TOPIC_INVENTORY_DEDUCT = "inventory-deduct";
}
