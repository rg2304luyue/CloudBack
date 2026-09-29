package org.cloudback.common.constant;

/**
 * Shared system constants.
 */
public final class SystemConstants {

    private SystemConstants() {
    }

    public static final String TOKEN_HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";

    public static final String REDIS_KEY_PREFIX = "cloud:";
    public static final String CART_KEY_PREFIX = REDIS_KEY_PREFIX + "cart:";
    public static final String TOKEN_BLACKLIST_KEY = REDIS_KEY_PREFIX + "token:blacklist:";
    public static final String PRODUCT_VIEWS_KEY = REDIS_KEY_PREFIX + "product:views";

    public static final Integer USER_STATUS_NORMAL = 1;
    public static final Integer USER_STATUS_DISABLED = 0;

    public static final String ROLE_BUYER = "BUYER";
    public static final String ROLE_SELLER = "SELLER";
    public static final String ROLE_ADMIN = "ADMIN";

    public static final String USER_ROLE_HEADER = "X-User-Role";
    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    public static final Integer PRODUCT_STATUS_OFF_SHELF = 0;
    public static final Integer PRODUCT_STATUS_PUBLISHED = 1;
    public static final Integer PRODUCT_STATUS_PENDING = 2;

    public static final Integer ORDER_STATUS_UNPAID = 0;
    public static final Integer ORDER_STATUS_PAID = 1;
    public static final Integer ORDER_STATUS_SHIPPED = 2;
    public static final Integer ORDER_STATUS_COMPLETED = 3;
    public static final Integer ORDER_STATUS_CANCELLED = 4;

    public static final String KAFKA_TOPIC_ORDER_CREATE = "order-create";
    public static final String KAFKA_TOPIC_PAYMENT_RESULT = "payment-result";
    public static final String KAFKA_TOPIC_INVENTORY_DEDUCT = "inventory-deduct";
    public static final String KAFKA_TOPIC_STOCK_RESTORE = "stock-restore";
    /** 订单取消后通知支付服务关闭支付单 */
    public static final String KAFKA_TOPIC_PAYMENT_CLOSE = "payment-close";
    /** 支付成功但订单已取消，请求支付服务退款 */
    public static final String KAFKA_TOPIC_REFUND_REQUEST = "refund-request";
    /** 下单成功后通知购物车服务清理已购商品（Feign 直调失败时的可靠补偿） */
    public static final String KAFKA_TOPIC_CART_CLEAR = "cart-clear";

    /** 支付状态 */
    public static final Integer PAYMENT_STATUS_UNPAID = 0;
    public static final Integer PAYMENT_STATUS_PAID = 1;
    public static final Integer PAYMENT_STATUS_FAILED = 2;
    public static final Integer PAYMENT_STATUS_CLOSED = 3;
    public static final Integer PAYMENT_STATUS_REFUNDED = 4;

    /** 登录失败计数 Redis key 前缀（防爆破） */
    public static final String LOGIN_FAIL_KEY = REDIS_KEY_PREFIX + "login:fail:";
    /** 两级缓存失效广播频道（跨实例同步 L1 失效） */
    public static final String CACHE_EVICT_CHANNEL = REDIS_KEY_PREFIX + "cache:evict";
    /** 缓存回源互斥锁 key 前缀（防击穿） */
    public static final String CACHE_LOCK_KEY = REDIS_KEY_PREFIX + "cache:lock:";

    public static final String MEILI_INDEX_PRODUCTS = "products";
}
