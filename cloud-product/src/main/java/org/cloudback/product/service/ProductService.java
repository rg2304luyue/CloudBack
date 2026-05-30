package org.cloudback.product.service;

import org.cloudback.common.result.R;
import org.cloudback.product.dto.ProductRequest;
import org.cloudback.product.model.entity.Category;
import org.cloudback.product.model.entity.Product;
import java.util.List;

/**
 * 商品服务接口，提供分类管理、商品 CRUD、库存扣减功能。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
public interface ProductService {

    /** 获取分类树（含子分类嵌套） */
    R<List<Category>> getCategoryTree();

    /** 添加分类 */
    R<String> addCategory(String role, Category category);

    /** 修改分类 */
    R<String> updateCategory(String role, Category category);

    /** 删除分类（有子分类时不允许删除） */
    R<String> deleteCategory(String role, Long id);

    /** 获取商品详情 */
    R<Product> getProductDetail(Long productId);

    /** 分页查询商品列表，支持分类和关键词筛选 */
    R<List<Product>> getProductList(Long categoryId, Integer page, Integer size, String keyword, String sortBy);

    /** 卖家查看自己的商品列表 */
    R<List<Product>> getMyProducts(Long userId, Integer page, Integer size);

    /** 添加商品 */
    R<String> addProduct(Long userId, String role, ProductRequest request);

    /** 修改商品 */
    R<String> updateProduct(Long userId, String role, Long id, ProductRequest request);

    /** 删除商品（逻辑删除） */
    R<String> deleteProduct(Long userId, String role, Long id);

    /** 扣减库存，同时增加销量，原子 UPDATE 防超卖 */
    R<String> deductStock(Long productId, Integer quantity);

    /** 回滚库存（取消订单/支付超时），同时减少销量 */
    R<String> restoreStock(Long productId, Integer quantity);

    /** 管理员：获取待审核商品列表 */
    R<List<Product>> getPendingProducts(Long page, Long size);

    /** 管理员：审核商品（通过/拒绝） */
    R<String> reviewProduct(Long id, boolean approved);

    /** 获取热门商品 Top8，按 Redis ZSET 浏览量排序，降级按销量 */
    R<List<Product>> getHotProducts();

    /** 按卖家 ID 获取商品列表 */
    R<List<Product>> getProductsBySellerId(Long sellerId);

    /** Meilisearch 全文搜索，支持中文分词和拼写纠错 */
    R<List<Product>> search(String keyword, Long categoryId, int page, int size);

    /** 搜索建议（自动补全） */
    R<List<String>> suggest(String prefix, int limit);
}
