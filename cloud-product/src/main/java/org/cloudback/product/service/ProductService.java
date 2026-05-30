package org.cloudback.product.service;

import org.cloudback.common.result.R;
import org.cloudback.product.dto.ProductRequest;
import org.cloudback.product.model.entity.Product;
import java.util.List;

/**
 * 商品服务接口，提供分类管理、商品 CRUD、库存扣减功能。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
public interface ProductService {

    // ==================== 商品查询 ====================

    /** 获取商品详情，含布隆过滤器前置拦截和两级缓存（Caffeine + Redis） */
    R<Product> getProductDetail(Long productId);

    /** 分页查询商品列表，支持分类筛选（含子分类）、关键词搜索（走 Meilisearch）、价格/销量排序 */
    R<List<Product>> getProductList(Long categoryId, Integer page, Integer size, String keyword, String sortBy);

    /** 卖家分页查看自己的商品列表，按创建时间降序 */
    R<List<Product>> getMyProducts(Long userId, Integer page, Integer size);

    /** 获取热门商品 Top8，优先按 Redis ZSET 浏览量排序，缓存未命中降级为数据库按销量排序 */
    R<List<Product>> getHotProducts();

    /** 按卖家 ID 获取商品列表（仅返回 ID，供订单服务 Feign 内部调用） */
    R<List<Product>> getProductsBySellerId(Long sellerId);

    /** Meilisearch 全文搜索，支持中文分词和拼写纠错，仅搜已上架商品 */
    R<List<Product>> search(String keyword, Long categoryId, int page, int size);

    /** 搜索建议（自动补全），基于 Meilisearch 商品名索引 */
    R<List<String>> suggest(String prefix, int limit);

    // ==================== 商品管理 ====================

    /** 添加商品，卖家需审核，管理员直接上架；写入后更新布隆过滤器和 Meilisearch 索引 */
    R<String> addProduct(Long userId, String role, ProductRequest request);

    /** 修改商品，卖家修改后重新进入待审核状态；更新后同步缓存驱逐和索引更新 */
    R<String> updateProduct(Long userId, String role, Long id, ProductRequest request);

    /** 逻辑删除商品，同时清理缓存和 Meilisearch 索引 */
    R<String> deleteProduct(Long userId, String role, Long id);

    // ==================== 库存操作（订单服务 Feign 调用） ====================

    /** 原子扣减库存（UPDATE SET stock=stock-N WHERE stock>=N），防止超卖；事务提交后驱逐缓存 */
    R<String> deductStock(Long productId, Integer quantity);

    /** 回滚库存（取消订单/支付超时时恢复），同时扣减销量；事务提交后驱逐缓存 */
    R<String> restoreStock(Long productId, Integer quantity);

    // ==================== 管理员审核 ====================

    /** 管理员分页获取待审核商品列表，按创建时间升序 */
    R<List<Product>> getPendingProducts(Long page, Long size);

    /** 审核商品（通过→上架并索引到 Meilisearch；拒绝→下架并从索引移除） */
    R<String> reviewProduct(Long id, boolean approved);
}
