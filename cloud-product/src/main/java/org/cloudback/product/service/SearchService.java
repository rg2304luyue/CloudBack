package org.cloudback.product.service;

import org.cloudback.product.model.entity.Product;
import java.util.List;

public interface SearchService {

    /** 索引单个商品（新增/更新时调用） */
    void indexProduct(Product product);

    /** 从索引中删除商品 */
    void deleteProduct(Long productId);

    /** 关键词搜索，返回商品 ID 列表 */
    List<Long> search(String keyword, Long categoryId, int page, int size);

    /** 搜索建议（自动补全），返回商品名列表 */
    List<String> suggest(String prefix, int limit);
}

