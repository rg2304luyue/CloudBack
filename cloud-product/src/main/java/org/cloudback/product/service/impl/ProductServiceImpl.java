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
import org.cloudback.product.dto.ProductRequest;
import org.cloudback.product.mapper.ProductMapper;
import org.cloudback.product.mapper.InventoryOperationMapper;
import org.cloudback.product.model.entity.InventoryOperation;
import org.cloudback.product.model.entity.Product;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final InventoryOperationMapper inventoryOperationMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final TwoLevelCacheService productDetailTwoLevel;
    private final TwoLevelCacheService hotProductsTwoLevel;
    private final TwoLevelCacheService productIdListTwoLevel;
    private final BloomFilterService bloomFilter;
    private final SearchService searchService;
    private final CategoryService categoryService;

    @Override
    public R<Product> getProductDetail(Long productId) {
        if (!bloomFilter.mightContain(productId)) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_EXIST);
        }

        String key = SystemConstants.REDIS_KEY_PREFIX + "product:detail:" + productId;
        // dbLoader 返回 null（不存在/已删除/未上架），由 TwoLevelCacheService 缓存空哨兵防穿透
        Product product = productDetailTwoLevel.get(key, Product.class, () -> {
            Product p = productMapper.selectById(productId);
            return isPublished(p) ? p : null;
        }, 300);

        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_EXIST);
        }

        try {
            redisTemplate.opsForZSet().incrementScore(SystemConstants.PRODUCT_VIEWS_KEY, productId.toString(), 1);
        } catch (Exception e) {
            log.warn("Increment product view score failed, productId={}", productId, e);
        }
        return R.ok(product);
    }

    @Override
    public R<List<Product>> getProductList(Long categoryId, Integer page, Integer size, String keyword, String sortBy) {
        if (keyword != null && !keyword.isEmpty()) {
            List<Long> searchIds = searchService.search(keyword, categoryId, page, size);
            List<Product> result = loadPublishedProducts(searchIds);
            long total = searchService.searchCount(keyword, categoryId);
            return R.ok(result, (int) total);
        }

        String cid = categoryId == null ? "all" : String.valueOf(categoryId);
        String idListKey = SystemConstants.REDIS_KEY_PREFIX + "product:ids:" + cid;

        @SuppressWarnings("unchecked")
        List<Long> allIds = productIdListTwoLevel.get(idListKey, List.class, () -> {
            LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
            wrapper.select(Product::getId)
                    .eq(Product::getStatus, SystemConstants.PRODUCT_STATUS_PUBLISHED);

            if (categoryId != null && categoryId > 0) {
                List<Long> categoryIds = categoryService.collectDescendantIds(categoryId);
                wrapper.in(Product::getCategoryId, categoryIds);
            }
            wrapper.orderByDesc(Product::getSales);
            return productMapper.selectList(wrapper).stream().map(Product::getId).collect(Collectors.toList());
        }, 120);

        if (allIds == null || allIds.isEmpty()) {
            return R.ok(Collections.emptyList(), 0);
        }

        List<Product> allProducts = loadPublishedProducts(allIds);
        if ("price_asc".equals(sortBy)) {
            allProducts.sort(Comparator.comparing(Product::getPrice, Comparator.nullsLast(Comparator.naturalOrder())));
        } else if ("price_desc".equals(sortBy)) {
            allProducts.sort(Comparator.comparing(Product::getPrice, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        }

        int total = allProducts.size();
        int fromIndex = Math.max(0, (page - 1) * size);
        if (fromIndex >= total) {
            return R.ok(Collections.emptyList(), total);
        }
        int toIndex = Math.min(fromIndex + size, total);
        return R.ok(allProducts.subList(fromIndex, toIndex), total);
    }

    @Override
    public R<List<Product>> getMyProducts(Long userId, Integer page, Integer size) {
        Page<Product> productPage = new Page<>(page, size);
        productMapper.selectPage(productPage, new LambdaQueryWrapper<Product>()
                .eq(Product::getSellerId, userId)
                .orderByDesc(Product::getCreateTime));
        return R.ok(productPage.getRecords(), (int) productPage.getTotal());
    }

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
                            List<Long> ids = topIds.stream().map(o -> Long.valueOf(o.toString())).collect(Collectors.toList());
                            List<Product> list = productMapper.selectBatchIds(ids).stream()
                                    .filter(this::isPublished)
                                    .collect(Collectors.toList());
                            if (CollUtil.isNotEmpty(list)) {
                                Map<Long, Integer> rankMap = new HashMap<>();
                                for (int i = 0; i < ids.size(); i++) {
                                    rankMap.put(ids.get(i), i);
                                }
                                list.sort(Comparator.comparingInt(p -> rankMap.getOrDefault(p.getId(), Integer.MAX_VALUE)));
                                return list;
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Load hot products from Redis failed, fallback to DB", e);
                    }
                    Page<Product> page = new Page<>(1, 8);
                    productMapper.selectPage(page, new LambdaQueryWrapper<Product>()
                            .eq(Product::getStatus, SystemConstants.PRODUCT_STATUS_PUBLISHED)
                            .orderByDesc(Product::getSales));
                    return page.getRecords();
                },
                300
        );
        return R.ok(products == null ? Collections.emptyList() : products);
    }

    @Override
    public R<List<Product>> getProductsBySellerId(Long sellerId) {
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getSellerId, sellerId)
                .select(Product::getId));
        return R.ok(products);
    }

    @Override
    public R<List<Product>> getProductsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return R.ok(Collections.emptyList());
        }
        List<Long> distinctIds = ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (distinctIds.isEmpty()) {
            return R.ok(Collections.emptyList());
        }
        return R.ok(productMapper.selectBatchIds(distinctIds));
    }

    @Override
    public R<String> updateProductStatus(Long id, Integer status) {
        Product dbProduct = productMapper.selectById(id);
        if (dbProduct == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_EXIST);
        }
        Product update = new Product();
        update.setId(id);
        update.setStatus(status);
        productMapper.updateById(update);
        evictProductAfterCommit(id);

        Product updatedProduct = productMapper.selectById(id);
        if (isPublished(updatedProduct)) {
            searchService.indexProduct(updatedProduct);
        } else {
            searchService.deleteProduct(id);
        }
        return R.ok("状态更新成功");
    }

    @Override
    public R<List<Product>> search(String keyword, Long categoryId, int page, int size) {
        List<Long> searchIds = searchService.search(keyword, categoryId, page, size);
        if (searchIds.isEmpty()) {
            return R.ok(Collections.emptyList(), 0);
        }
        List<Product> result = loadPublishedProducts(searchIds);
        long total = searchService.searchCount(keyword, categoryId);
        return R.ok(result, (int) total);
    }

    @Override
    public R<List<String>> suggest(String prefix, int limit) {
        return R.ok(searchService.suggest(prefix, limit));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<String> addProduct(Long userId, String role, ProductRequest request) {
        checkProductManagePermission(role);
        Product product = new Product();
        applyProductRequest(product, request);
        product.setSellerId(userId);
        product.setSales(0);
        product.setStatus(SystemConstants.ROLE_ADMIN.equals(role)
                ? SystemConstants.PRODUCT_STATUS_PUBLISHED
                : SystemConstants.PRODUCT_STATUS_PENDING);

        productMapper.insert(product);
        bloomFilter.addProductId(product.getId());
        hotProductsTwoLevel.evict(SystemConstants.REDIS_KEY_PREFIX + "product:hot");
        evictProductIdListCache();

        if (SystemConstants.PRODUCT_STATUS_PUBLISHED.equals(product.getStatus())) {
            searchService.indexProduct(product);
        }
        return R.ok(SystemConstants.ROLE_ADMIN.equals(role) ? "添加商品成功" : "添加成功，等待管理员审核");
    }

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
        product.setStatus(SystemConstants.ROLE_SELLER.equals(role)
                ? SystemConstants.PRODUCT_STATUS_PENDING
                : dbProduct.getStatus());

        productMapper.updateById(product);
        evictProductAfterCommit(id);

        Product updatedProduct = productMapper.selectById(id);
        if (isPublished(updatedProduct)) {
            searchService.indexProduct(updatedProduct);
        } else {
            searchService.deleteProduct(id);
        }
        return R.ok(SystemConstants.ROLE_SELLER.equals(role) ? "已重新提交审核" : "更新商品成功");
    }

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
        evictProductIdListCache();
        searchService.deleteProduct(id);
        return R.ok("删除商品成功");
    }

    @Override
    public R<List<Product>> getPendingProducts(Long page, Long size) {
        Page<Product> productPage = new Page<>(page, size);
        productMapper.selectPage(productPage, new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, SystemConstants.PRODUCT_STATUS_PENDING)
                .orderByAsc(Product::getCreateTime));
        return R.ok(productPage.getRecords(), (int) productPage.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<String> reviewProduct(Long id, boolean approved) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_EXIST);
        }
        if (!SystemConstants.PRODUCT_STATUS_PENDING.equals(product.getStatus())) {
            return R.fail("该商品无需审核或已处理");
        }
        product.setStatus(approved ? SystemConstants.PRODUCT_STATUS_PUBLISHED : SystemConstants.PRODUCT_STATUS_OFF_SHELF);
        productMapper.updateById(product);
        evictProductAfterCommit(id);

        if (approved) {
            searchService.indexProduct(product);
        } else {
            searchService.deleteProduct(id);
        }
        return R.ok(approved ? "审核通过" : "已拒绝，商品已下架");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<String> deductStock(Long productId, Integer quantity, String orderNo) {
        InventoryOperation operation = lockInventoryOperation(productId, quantity, orderNo);
        if ("RESERVED".equals(operation.getState())) {
            return R.ok("扣减库存成功");
        }
        if ("RESTORED".equals(operation.getState())) {
            throw new BusinessException("该订单库存操作已关闭，不能再次扣减");
        }
        int affected = productMapper.deductStockAtomically(productId, quantity);
        if (affected == 0) {
            Product product = productMapper.selectById(productId);
            if (!isPublished(product)) {
                throw new BusinessException(ResultCode.PRODUCT_NOT_EXIST);
            }
            throw new BusinessException(ResultCode.STOCK_INSUFFICIENT);
        }
        inventoryOperationMapper.setState(orderNo, productId, "RESERVED");
        // 库存变动不改变商品 ID 列表的成员，只影响详情/热点缓存；ID 列表依赖其自身 TTL 过期即可
        evictProductAfterCommit(productId, false);
        return R.ok("扣减库存成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<String> restoreStock(Long productId, Integer quantity, String orderNo) {
        InventoryOperation operation = lockInventoryOperation(productId, quantity, orderNo);
        if ("RESTORED".equals(operation.getState())) {
            return R.ok("回滚库存成功");
        }
        if ("RESERVED".equals(operation.getState())) {
            int affected = productMapper.restoreStock(productId, quantity);
            if (affected == 0) {
                // 商品已删除或销量异常时跳过库存回补，但仍标记 RESTORED 保持幂等，
                // 避免订单端补偿永久重试。仅 DB 异常等真正失败才抛出。
                Product product = productMapper.selectById(productId);
                if (product == null) {
                    log.warn("商品不存在或已删除，跳过库存回补但仍标记恢复完成: productId={}, orderNo={}, quantity={}",
                            productId, orderNo, quantity);
                } else {
                    // 销量被人工改库等异常场景：库存不会回流，属于需人工核对的资金/库存差异
                    log.error("商品销量异常(sales={} < quantity={})，跳过库存回补但仍标记恢复完成，需人工核对: productId={}, orderNo={}",
                            product.getSales(), quantity, productId, orderNo);
                }
            } else {
                evictProductAfterCommit(productId, false);
            }
        }
        // An early restore closes the key without adding inventory; a late deduct must fail.
        inventoryOperationMapper.setState(orderNo, productId, "RESTORED");
        return R.ok("回滚库存成功");
    }

    private InventoryOperation lockInventoryOperation(Long productId, Integer quantity, String orderNo) {
        if (productId == null || productId <= 0 || quantity == null || quantity <= 0
                || orderNo == null || orderNo.isBlank() || orderNo.length() > 32
                || !orderNo.equals(orderNo.trim())) {
            throw new BusinessException("库存操作参数无效");
        }
        inventoryOperationMapper.ensureExists(orderNo, productId, quantity);
        InventoryOperation operation = inventoryOperationMapper.lock(orderNo, productId);
        if (operation == null || !quantity.equals(operation.getQuantity())) {
            throw new BusinessException("同一订单商品的库存操作数量不一致");
        }
        if (operation.getState() == null
                || !Set.of("EMPTY", "RESERVED", "RESTORED").contains(operation.getState())) {
            throw new BusinessException("库存操作状态异常");
        }
        return operation;
    }

    @PostConstruct
    public void initBloomFilter() {
        List<Product> allProducts = productMapper.selectList(new LambdaQueryWrapper<Product>().select(Product::getId));
        for (Product p : allProducts) {
            bloomFilter.addProductId(p.getId());
        }
        log.info("Loaded {} product ids into bloom filter", allProducts.size());
    }

    @PostConstruct
    public void initSearchIndex() {
        try {
            List<Product> publishedProducts = productMapper.selectList(
                    new LambdaQueryWrapper<Product>().eq(Product::getStatus, SystemConstants.PRODUCT_STATUS_PUBLISHED));
            for (Product product : publishedProducts) {
                searchService.indexProduct(product);
            }
            log.info("Rebuilt Meilisearch product index, count={}", publishedProducts.size());
        } catch (Exception e) {
            log.warn("Rebuild Meilisearch index failed: {}", e.getMessage());
        }
    }

    private List<Product> loadPublishedProducts(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return ids.stream()
                .map(this::loadPublishedProductOrNull)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Product loadPublishedProductOrNull(Long id) {
        try {
            return getProductDetail(id).getData();
        } catch (BusinessException e) {
            return null;
        }
    }

    private void applyProductRequest(Product product, ProductRequest request) {
        product.setCategoryId(request.categoryId());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setMainImage(request.mainImage());
        product.setImages(request.images());
    }

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

    private boolean isPublished(Product product) {
        return product != null && SystemConstants.PRODUCT_STATUS_PUBLISHED.equals(product.getStatus());
    }

    private void evictProductAfterCommit(Long productId) {
        evictProductAfterCommit(productId, true);
    }

    /**
     * @param includeIdList 是否需要失效商品 ID 列表缓存；结构性变更（新增/删除/上下架/改资料）为 true，
     *                      仅库存变动为 false（列表成员不变，避免高频下单路径反复扫描/广播）
     */
    private void evictProductAfterCommit(Long productId, boolean includeIdList) {
        String hotKey = SystemConstants.REDIS_KEY_PREFIX + "product:hot";
        String detailKey = SystemConstants.REDIS_KEY_PREFIX + "product:detail:" + productId;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    hotProductsTwoLevel.evict(hotKey);
                    productDetailTwoLevel.evict(detailKey);
                    if (includeIdList) {
                        evictProductIdListCache();
                    }
                }
            });
        } else {
            hotProductsTwoLevel.evict(hotKey);
            productDetailTwoLevel.evict(detailKey);
            if (includeIdList) {
                evictProductIdListCache();
            }
        }
    }

    /** 按模式失效所有商品 ID 列表缓存（cloud:product:ids:*），并跨实例广播 L1 失效 */
    private void evictProductIdListCache() {
        try {
            productIdListTwoLevel.evictByPattern(SystemConstants.REDIS_KEY_PREFIX + "product:ids:*");
        } catch (Exception e) {
            log.warn("Evict product id list cache failed: {}", e.getMessage());
        }
    }
}
