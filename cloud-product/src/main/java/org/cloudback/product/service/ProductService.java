package org.cloudback.product.service;

import org.cloudback.common.result.R;
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
    R<String> addCategory(Long userId, String role, Category category);

    /** 修改分类 */
    R<String> updateCategory(Long userId, String role, Category category);

    /** 删除分类（有子分类时不允许删除） */
    R<String> deleteCategory(Long userId, String role, Long id);

    /** 获取商品详情 */
    R<Product> getProductDetail(Long productId);

    /** 分页查询商品列表，支持分类和关键词筛选 */
    R<List<Product>> getProductList(Long categoryId, Integer page, Integer size, String keyword);

    /** 卖家查看自己的商品列表 */
    R<List<Product>> getMyProducts(Long userId, Integer page, Integer size);

    /** 添加商品 */
    R<String> addProduct(Long userId, String role, Product product);

    /** 修改商品 */
    R<String> updateProduct(Long userId, String role, Product product);

    /** 删除商品（逻辑删除） */
    R<String> deleteProduct(Long userId, String role, Long id);

    /** 扣减库存，同时增加销量，带事务保护 */
    R<String> deductStock(Long productId, Integer quantity);
}
