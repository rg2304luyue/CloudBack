package org.cloudback.common.constant;

/**
 * 系统常量，统一管理 Token、Redis Key 前缀、订单状态、Kafka Topic 等常量。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
public final class SystemConstants {

    private SystemConstants() {
    }

    /** Token 请求头名称 */
    public static final String TOKEN_HEADER = "Authorization";
    /** Token 前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** Redis Key 统一前缀 */
    public static final String REDIS_KEY_PREFIX = "cloud:";
    /** 购物车 Redis Key 前缀: cloud:cart:{userId} */
    public static final String CART_KEY_PREFIX = REDIS_KEY_PREFIX + "cart:";
    /** Token 黑名单 Key 前缀 */
    public static final String TOKEN_BLACKLIST_KEY = REDIS_KEY_PREFIX + "token:blacklist:";
    /** 商品浏览量 ZSET Key */
    public static final String PRODUCT_VIEWS_KEY = REDIS_KEY_PREFIX + "product:views";

    /** 用户状态: 正常 */
    public static final Integer USER_STATUS_NORMAL = 1;
    /** 用户状态: 禁用 */
    public static final Integer USER_STATUS_DISABLED = 0;

    /** 角色: 买家 */
    public static final String ROLE_BUYER = "BUYER";
    /** 角色: 卖家 */
    public static final String ROLE_SELLER = "SELLER";
    /** 角色: 管理员 */
    public static final String ROLE_ADMIN = "ADMIN";

    /** Gateway 注入的用户角色请求头 */
    public static final String USER_ROLE_HEADER = "X-User-Role";

    /** 商品状态: 待审核 */
    public static final Integer PRODUCT_STATUS_PENDING = 2;

    /** 订单状态: 待支付 */
    public static final Integer ORDER_STATUS_UNPAID = 0;
    /** 订单状态: 已支付 */
    public static final Integer ORDER_STATUS_PAID = 1;
    /** 订单状态: 已发货 */
    public static final Integer ORDER_STATUS_SHIPPED = 2;
    /** 订单状态: 已完成 */
    public static final Integer ORDER_STATUS_COMPLETED = 3;
    /** 订单状态: 已取消 */
    public static final Integer ORDER_STATUS_CANCELLED = 4;

    /** Kafka Topic: 订单创建 */
    public static final String KAFKA_TOPIC_ORDER_CREATE = "order-create";
    /** Kafka Topic: 支付结果 */
    public static final String KAFKA_TOPIC_PAYMENT_RESULT = "payment-result";
    /** Kafka Topic: 库存扣减 */
    public static final String KAFKA_TOPIC_INVENTORY_DEDUCT = "inventory-deduct";

    /** Meilisearch 商品索引名 */
    public static final String MEILI_INDEX_PRODUCTS = "products";
}
