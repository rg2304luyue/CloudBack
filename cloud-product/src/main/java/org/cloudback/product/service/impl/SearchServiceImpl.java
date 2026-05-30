package org.cloudback.product.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.model.Searchable;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.product.model.entity.Product;
import org.cloudback.product.service.SearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "meili", name = "host")
public class SearchServiceImpl implements SearchService {

    private final Client client;

    /** 获取 Meilisearch 商品索引实例 */
    private Index getIndex() {
        return client.index(SystemConstants.MEILI_INDEX_PRODUCTS);
    }

    /** 启动时初始化 Meilisearch 索引：创建索引 → 设置筛选/搜索/排序字段 */
    @PostConstruct
    public void init() {
        try {
            client.createIndex(SystemConstants.MEILI_INDEX_PRODUCTS, "id");
            getIndex().updateFilterableAttributesSettings(new String[]{"categoryId", "status"});
            getIndex().updateSearchableAttributesSettings(new String[]{"name", "description"});
            getIndex().updateSortableAttributesSettings(new String[]{"price", "sales", "createTime"});
            log.info("Meilisearch 索引初始化完成");
        } catch (Exception e) {
            log.warn("Meilisearch 索引初始化异常（可能已存在）: {}", e.getMessage());
        }
    }

    /** 将商品文档同步到 Meilisearch 索引（以商品 ID 为主键，覆盖更新） */
    @Override
    public void indexProduct(Product product) {
        try {
            JSONObject doc = new JSONObject();
            doc.put("id", product.getId().toString());
            doc.put("name", product.getName());
            doc.put("description", product.getDescription() != null ? product.getDescription() : "");
            doc.put("price", product.getPrice());
            doc.put("sales", product.getSales());
            doc.put("mainImage", product.getMainImage());
            doc.put("categoryId", product.getCategoryId());
            doc.put("sellerId", product.getSellerId());
            doc.put("status", product.getStatus());
            doc.put("createTime", product.getCreateTime() != null ? product.getCreateTime().toString() : "");

            getIndex().addDocuments(JSON.toJSONString(Collections.singletonList(doc)), "id");
            log.debug("已同步商品到 Meilisearch: productId={}", product.getId());
        } catch (Exception e) {
            log.error("同步商品到 Meilisearch 失败: productId={}", product.getId(), e);
        }
    }

    @Override
    public void deleteProduct(Long productId) {
        try {
            getIndex().deleteDocument(String.valueOf(productId));
            log.debug("已从 Meilisearch 删除商品: productId={}", productId);
        } catch (Exception e) {
            log.error("从 Meilisearch 删除商品失败: productId={}", productId, e);
        }
    }

    /** Meilisearch 全文搜索：仅搜 status=1 的上架商品，支持分类过滤，返回商品 ID 列表 */
    @Override
    public List<Long> search(String keyword, Long categoryId, int page, int size) {
        try {
            SearchRequest.SearchRequestBuilder builder = SearchRequest.builder()
                    .q(keyword)
                    .page(page)
                    .hitsPerPage(size);

            // 只搜已上架商品
            builder.filter(new String[]{"status = 1"});
            if (categoryId != null && categoryId > 0) {
                builder.filter(new String[]{"status = 1 AND categoryId = " + categoryId});
            }

            Searchable result = getIndex().search(builder.build());
            return result.getHits().stream()
                    .map(hit -> Long.valueOf(hit.get("id").toString()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Meilisearch 搜索异常: keyword={}", keyword, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<String> suggest(String prefix, int limit) {
        try {
            Searchable result = getIndex().search(
                    SearchRequest.builder()
                            .q(prefix)
                            .limit(limit)
                            .attributesToRetrieve(new String[]{"name"})
                            .build()
            );
            return result.getHits().stream()
                    .map(hit -> hit.get("name").toString())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Meilisearch 搜索建议异常: prefix={}", prefix, e);
            return Collections.emptyList();
        }
    }
}
