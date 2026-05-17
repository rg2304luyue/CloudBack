package org.cloudback.product.service;

import org.cloudback.common.result.R;
import org.cloudback.product.model.entity.Category;
import org.cloudback.product.model.entity.Product;
import java.util.List;

public interface ProductService {
    // ---- 分类 ----
    R<List<Category>> getCategoryTree();

    R<String> addCategory(Category category);

    R<String> updateCategory(Category category);

    R<String> deleteCategory(Long id);

    // ---- 商品 ----
    R<Product> getProductDetail(Long productId);

    R<List<Product>> getProductList(Long categoryId, Integer page, Integer size, String keyword);

    R<String> addProduct(Product product);

    R<String> updateProduct(Product product);

    R<String> deleteProduct(Long id);

    // ---- 库存 ----
    R<String> deductStock(Long productId, Integer quantity);
}
