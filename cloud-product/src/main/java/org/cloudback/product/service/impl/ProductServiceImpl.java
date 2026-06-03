package org.cloudback.product.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.config.BloomFilterService;
import org.cloudback.common.config.TwoLevelCacheService;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.common.exception.BusinessException;
import org.cloudback.common.result.R;
import org.cloudback.common.result.ResultCode;
import org.cloudback.product.mapper.ProductMapper;
import org.cloudback.product.model.entity.Product;
import org.cloudback.product.dto.ProductRequest;
import org.cloudback.product.service.CategoryService;
import org.cloudback.product.service.ProductService;
import org.cloudback.product.service.SearchService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商品服务实现，处理分类树、商品 CRUD、分页搜索、库存扣减。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private final TwoLevelCacheService productDetailTwoLevel;
    private final TwoLevelCacheService hotProductsTwoLevel;
    private final TwoLevelCacheService productIdListTwoLevel;
    private final BloomFilterService bloomFilter;
    private final SearchService searchService;
    private final CategoryService categoryService;

    // ==================== 商品查询 ====================

    /** 获取商品详情：布隆过滤器前置拦截 → 两级缓存(Caffeine+Redis) → DB；访问时异步递增浏览量 ZSET */
    @Override
    public R<Product> getProductDetail(Long productId) {
        if (!bloomFilter.mightContain(productId)) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_EXIST);
        }

        String key = SystemConstants.REDIS_KEY_PREFIX + "product:detail:" + productId;
        Product product = productDetailTwoLevel.get(key, Product.class,
                () -> {
                    Product p = productMapper.selectById(productId);
                    if (p == null) throw new BusinessException(ResultCode.PRODUCT_NOT_EXIST);
                    return p;
                },
                300
        );

        try {
            redisTemplate.opsForZSet().incrementScore(
                    SystemConstants.PRODUCT_VIEWS_KEY, productId.toString(), 1);
        } catch (Exception e) {
            log.warn("Redis浏览量+1失败, productId={}", productId, e);
        }
        return R.ok(product);
    }

    /** 分页商品列表：有关键词走 Meilisearch 搜索 → 无关键词走 ID 缓存 + 二级缓存加载详情 → 内存排序 → 手动分页 */
    @Override
    public R<List<Product>> getProductList(Long categoryId, Integer page, Integer size, String keyword, String sortBy) {
        // 有 keyword 时走 Meilisearch 全文搜索
        if (keyword != null && !keyword.isEmpty()) {
            List<Long> searchIds = searchService.search(keyword, categoryId, page, size);
            if (searchIds.isEmpty()) {
                return R.ok(Collections.emptyList(), 0);
            }
            List<Product> result = searchIds.stream()
                    .map(id -> productDetailTwoLevel.get(
                            SystemConstants.REDIS_KEY_PREFIX + "product:detail:" + id,
                            Product.class,
                            () -> productMapper.selectById(id),
                            300))
                    .collect(Collectors.toList());
            long total = searchService.searchCount(keyword, categoryId);
            return R.ok(result, (int) total);
        }

        // 无 keyword，走 ID 缓存
        String cid = categoryId == null ? "all" : String.valueOf(categoryId);
        String idListKey = SystemConstants.REDIS_KEY_PREFIX + "product:ids:" + cid;

        @SuppressWarnings("unchecked")
        List<Long> allIds = productIdListTwoLevel.get(idListKey, List.class, () -> {
            LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
            wrapper.select(Product::getId);
            wrapper.eq(Product::getStatus, 1);

            if (categoryId != null && categoryId > 0) {
                List<Long> categoryIds = categoryService.collectDescendantIds(categoryId);
                wrapper.in(Product::getCategoryId, categoryIds);
            }
            wrapper.orderByDesc(Product::getSales);

            return productMapper.selectList(wrapper).stream()
                    .map(Product::getId)
                    .collect(Collectors.toList());
        }, 120);

        if (allIds == null || allIds.isEmpty()) {
            return R.ok(Collections.emptyList(), 0);
        }

        // 加载全部商品（用于排序）
        List<Product> allProducts = allIds.stream().map(id ->
                productDetailTwoLevel.get(
                        SystemConstants.REDIS_KEY_PREFIX + "product:detail:" + id,
                        Product.class,
                        () -> productMapper.selectById(id),
                        300
                )
        ).collect(Collectors.toList());

        // 安全排序（null price 排最后）
        if ("price_asc".equals(sortBy)) {
            allProducts.sort(Comparator.nullsLast(Comparator.comparing(Product::getPrice)));
        } else if ("price_desc".equals(sortBy)) {
            allProducts.sort(Comparator.nullsLast(Comparator.comparing(Product::getPrice).reversed()));
        }

        // 手动分页
        int total = allProducts.size();
        int fromIndex = (page - 1) * size;
        if (fromIndex >= total) {
            return R.ok(Collections.emptyList(), 0);
        }
        int toIndex = Math.min(fromIndex + size, total);
        List<Product> result = allProducts.subList(fromIndex, toIndex);

        return R.ok(result, total);
    }

    /** 卖家分页查看自己的商品列表，按创建时间降序，使用 MyBatis-Plus 分页插件 */
    @Override
    public R<List<Product>> getMyProducts(Long userId, Integer page, Integer size) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getSellerId, userId);
        wrapper.orderByDesc(Product::getCreateTime);
        Page<Product> productPage = new Page<>(page, size);
        productMapper.selectPage(productPage, wrapper);
        return R.ok(productPage.getRecords(), (int) productPage.getTotal());
    }

    /** 添加商品：管理员直接上架，卖家需审核；写入后更新布隆过滤器、驱逐热门缓存、同步 Meilisearch */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<String> addProduct(Long userId, String role, ProductRequest request) {
        checkProductManagePermission(role);
        Product product = new Product();
        applyProductRequest(product, request);
        product.setSellerId(userId);
        product.setSales(0);
        product.setStatus(SystemConstants.ROLE_ADMIN.equals(role) ? 1 : SystemConstants.PRODUCT_STATUS_PENDING);

        productMapper.insert(product);
        bloomFilter.addProductId(product.getId());
        hotProductsTwoLevel.evict(SystemConstants.REDIS_KEY_PREFIX + "product:hot");

        if (product.getStatus() == 1) {
            searchService.indexProduct(product);
        }
        return R.ok(SystemConstants.ROLE_ADMIN.equals(role) ? "添加商品成功" : "添加成功，等待管理员审核");
    }

    /** 修改商品：权限校验 → 卖家修改重新进入待审核 → 更新 DB → 驱逐缓存 → 同步索引 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<String> updateProduct(Long userId, String role, Long id, ProductRequest request) {
        checkProductManagePermission(role);
        Product dbProduct = productMapper.selectById(id);
        if (dbProduct == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_EXIST);
        }
        checkProductPermission(userId, role, dbProduct);
        Product product = new Product();
        product.setId(id);
        applyProductRequest(product, request);
        product.setSellerId(dbProduct.getSellerId());
        // 管理员编辑保持原状态，卖家修改进入待审核
        if (SystemConstants.ROLE_SELLER.equals(role)) {
            product.setStatus(SystemConstants.PRODUCT_STATUS_PENDING);
        } else {
            product.setStatus(dbProduct.getStatus());
        }

        productMapper.updateById(product);
        String hotKey = SystemConstants.REDIS_KEY_PREFIX + "product:hot";
        String detailKey = SystemConstants.REDIS_KEY_PREFIX + "product:detail:" + id;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                hotProductsTwoLevel.evict(hotKey);
                productDetailTwoLevel.evict(detailKey);
            }
        });

        Product updatedProduct = productMapper.selectById(id);
        if (updatedProduct.getStatus() == 1) {
            searchService.indexProduct(updatedProduct);
        } else {
            searchService.deleteProduct(id);
        }
        return R.ok(SystemConstants.ROLE_SELLER.equals(role) ? "已重新提交审核" : "更新商品成功");
    }

    /** 逻辑删除商品：权限校验 → 逻辑删除 → 驱逐缓存 → 从 Meilisearch 索引移除 */
    @Override
    public R<String> deleteProduct(Long userId, String role, Long id) {
        checkProductManagePermission(role);
        Product dbProduct = productMapper.selectById(id);
        if (dbProduct == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_EXIST);
        }
        checkProductPermission(userId, role, dbProduct);
        productMapper.deleteById(id);

        hotProductsTwoLevel.evict(SystemConstants.REDIS_KEY_PREFIX + "product:hot");
        productDetailTwoLevel.evict(SystemConstants.REDIS_KEY_PREFIX + "product:detail:" + id);

        searchService.deleteProduct(id);
        return R.ok("删除商品成功");
    }

    // ==================== 审核 ====================

    /** 管理员分页获取待审核商品列表，按创建时间升序 */
    @Override
    public R<List<Product>> getPendingProducts(Long page, Long size) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, SystemConstants.PRODUCT_STATUS_PENDING)
               .orderByAsc(Product::getCreateTime);
        Page<Product> productPage = new Page<>(page, size);
        productMapper.selectPage(productPage, wrapper);
        return R.ok(productPage.getRecords(), (int) productPage.getTotal());
    }

    /** 审核商品：通过→上架并索引到 Meilisearch，拒绝→下架并从索引移除；驱逐相关缓存 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<String> reviewProduct(Long id, boolean approved) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_EXIST);
        }
        // null-safe 检查
        if (product.getStatus() == null || !SystemConstants.PRODUCT_STATUS_PENDING.equals(product.getStatus())) {
            return R.fail("该商品无需审核或已处理");
        }
        product.setStatus(approved ? 1 : 0);
        productMapper.updateById(product);

        hotProductsTwoLevel.evict(SystemConstants.REDIS_KEY_PREFIX + "product:hot");
        productDetailTwoLevel.evict(SystemConstants.REDIS_KEY_PREFIX + "product:detail:" + id);

        if (approved) {
            searchService.indexProduct(product);
        } else {
            searchService.deleteProduct(id);
        }
        return R.ok(approved ? "审核通过" : "已拒绝，商品已下架");
    }

    // ==================== 热门商品 ====================

    /** 获取热门商品 Top8：优先从 Redis ZSET 取浏览量 Top8 → 批量查缓存加载详情；降级为数据库按销量排序 */
    @Override
    public R<List<Product>> getHotProducts() {
        List<Product> products = hotProductsTwoLevel.get(
                SystemConstants.REDIS_KEY_PREFIX + "product:hot",
                List.class,
                () -> {
                    try {
                        Set<Object> topIds = redisTemplate.opsForZSet()
                                .reverseRange(SystemConstants.PRODUCT_VIEWS_KEY, 0, 7);
                        if (CollUtil.isNotEmpty(topIds)) {
                            List<Long> ids = topIds.stream()
                                    .map(o -> Long.valueOf(o.toString()))
                                    .collect(Collectors.toList());
                            List<Product> list = productMapper.selectBatchIds(ids);
                            if (CollUtil.isNotEmpty(list)) {
                                // 按 ZSet 排名顺序排序 O(n)，避免 indexOf O(n²)
                                Map<Long, Integer> rankMap = new HashMap<>();
                                for (int i = 0; i < ids.size(); i++) {
                                    rankMap.put(ids.get(i), i);
                                }
                                list.sort(Comparator.comparingInt(p ->
                                        rankMap.getOrDefault(p.getId(), Integer.MAX_VALUE)));
                                return list;
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Redis查询热门商品失败，降级为数据库查询", e);
                    }
                    // 降级：按销量排序
                    LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(Product::getStatus, 1)
                            .orderByDesc(Product::getSales);
                    Page<Product> page = new Page<>(1, 8);
                    productMapper.selectPage(page, wrapper);
                    return page.getRecords();
                },
                300
        );
        return R.ok(products);
    }

    /** 将 ProductRequest 的公共字段映射到 Product 实体 */
    private void applyProductRequest(Product product, ProductRequest request) {
        product.setCategoryId(request.categoryId());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setMainImage(request.mainImage());
        product.setImages(request.images());
    }

    // ==================== 权限校验 ====================

    private void checkProductManagePermission(String role) {
        if (!SystemConstants.ROLE_SELLER.equals(role) && !SystemConstants.ROLE_ADMIN.equals(role)) {
            throw new BusinessException(ResultCode.SELLER_ONLY);
        }
    }

    private void checkProductPermission(Long userId, String role, Product product) {
        if (SystemConstants.ROLE_ADMIN.equals(role)) return;
        if (product.getSellerId() != null && product.getSellerId().equals(userId)) return;
        throw new BusinessException(ResultCode.NOT_YOUR_PRODUCT);
    }

    // ==================== 库存操作（订单服务 Feign 调用） ====================

    /** 原子扣减库存：UPDATE stock = stock - N, sales = sales + N WHERE stock >= N；事务提交后驱逐缓存 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<String> deductStock(Long productId, Integer quantity) {
        int affected = productMapper.deductStockAtomically(productId, quantity);
        if (affected == 0) {
            Product product = productMapper.selectById(productId);
            if (product == null || (product.getDeleted() != null && product.getDeleted() == 1)) {
                throw new BusinessException(ResultCode.PRODUCT_NOT_EXIST);
            }
            throw new BusinessException(ResultCode.STOCK_INSUFFICIENT);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                productDetailTwoLevel.evict(SystemConstants.REDIS_KEY_PREFIX + "product:detail:" + productId);
                hotProductsTwoLevel.evict(SystemConstants.REDIS_KEY_PREFIX + "product:hot");
            }
        });
        return R.ok("扣减库存成功");
    }

    /** 回滚库存：UPDATE stock = stock + N, sales = sales - N，校验影响行数；事务提交后驱逐缓存 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<String> restoreStock(Long productId, Integer quantity) {
        int affected = productMapper.restoreStock(productId, quantity);
        if (affected == 0) {
            log.warn("回滚库存影响0行，商品可能已删除或不存在: productId={}", productId);
            throw new BusinessException(ResultCode.PRODUCT_NOT_EXIST);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                productDetailTwoLevel.evict(SystemConstants.REDIS_KEY_PREFIX + "product:detail:" + productId);
                hotProductsTwoLevel.evict(SystemConstants.REDIS_KEY_PREFIX + "product:hot");
            }
        });
        return R.ok("回滚库存成功");
    }

    /** 启动时加载所有商品 ID 到布隆过滤器 */
    @PostConstruct
    public void initBloomFilter() {
        List<Product> allProducts = productMapper.selectList(
                new LambdaQueryWrapper<Product>().select(Product::getId)
        );
        for (Product p : allProducts) {
            bloomFilter.addProductId(p.getId());
        }
        log.info("已加载 {} 条商品 ID 到布隆过滤器", allProducts.size());
    }

    /** 启动时重建 Meilisearch 全文索引 */
    @PostConstruct
    public void initSearchIndex() {
        try {
            List<Product> publishedProducts = productMapper.selectList(
                    new LambdaQueryWrapper<Product>().eq(Product::getStatus, 1));
            for (Product product : publishedProducts) {
                searchService.indexProduct(product);
            }
            log.info("已重建 Meilisearch 索引，共 {} 条上架商品", publishedProducts.size());
        } catch (Exception e) {
            log.warn("重建 Meilisearch 索引失败（Meilisearch 可能未启动）: {}", e.getMessage());
        }
    }

    /** 按卖家 ID 获取商品列表（仅返回 ID，供订单服务 Feign 内部查询使用） */
    @Override
    public R<List<Product>> getProductsBySellerId(Long sellerId) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(Product::getSellerId, sellerId)
                .select(Product::getId);
        List<Product> products = productMapper.selectList(wrapper);
        return R.ok(products);
    }

    /** Meilisearch 全文搜索 */
    @Override
    public R<List<Product>> search(String keyword, Long categoryId, int page, int size) {
        List<Long> searchIds = searchService.search(keyword, categoryId, page, size);
        if (searchIds.isEmpty()) {
            return R.ok(Collections.emptyList(), 0);
        }
        List<Product> result = searchIds.stream()
                .map(id -> productDetailTwoLevel.get(
                        SystemConstants.REDIS_KEY_PREFIX + "product:detail:" + id,
                        Product.class,
                        () -> productMapper.selectById(id),
                        300))
                .collect(Collectors.toList());
        long total = searchService.searchCount(keyword, categoryId);
        return R.ok(result, (int) total);
    }

    /** 搜索建议（自动补全） */
    @Override
    public R<List<String>> suggest(String prefix, int limit) {
        return R.ok(searchService.suggest(prefix, limit));
    }
}
