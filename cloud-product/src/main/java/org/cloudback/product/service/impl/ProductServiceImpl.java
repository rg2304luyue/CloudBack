package org.cloudback.product.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.config.TwoLevelCacheService;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.common.exception.BusinessException;
import org.cloudback.common.result.R;
import org.cloudback.common.result.ResultCode;
import org.cloudback.product.mapper.CategoryMapper;
import org.cloudback.product.mapper.ProductMapper;
import org.cloudback.product.model.entity.Category;
import org.cloudback.product.model.entity.Product;
import org.cloudback.product.service.ProductService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
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

    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private final TwoLevelCacheService productDetailTwoLevel;
    private final TwoLevelCacheService hotProductsTwoLevel;
    private final TwoLevelCacheService productIdListTwoLevel;

    // ==================== 分类 ====================

    /** 获取分类树：查所有分类 → 按 parentId 分组 → 递归组装 */
    @Override
    public R<List<Category>> getCategoryTree() {
        List<Category> allCategories = categoryMapper.selectList(null);

        Map<Long, List<Category>> parentMap = allCategories.stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() == null ? 0L : c.getParentId()));

        List<Category> roots = parentMap.getOrDefault(0L, new ArrayList<>());

        for (Category root : roots) {
            fillChildren(root, parentMap);
        }
        return R.ok(roots);
    }

    /** 递归填充子分类 */
    private void fillChildren(Category parent, Map<Long, List<Category>> parentMap) {
        List<Category> children = parentMap.get(parent.getId());
        if (children != null) {
            parent.setChildren(children);
            for (Category child : children) {
                fillChildren(child, parentMap);
            }
        }
    }

    @Override
    public R<String> addCategory(Long userId, String role, Category category) {
        checkProductManagePermission(role);
        categoryMapper.insert(category);
        return R.ok("添加分类成功");
    }

    @Override
    public R<String> updateCategory(Long userId, String role, Category category) {
        checkProductManagePermission(role);
        Category dbCategory = categoryMapper.selectById(category.getId());
        if (dbCategory == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "分类不存在");
        }
        categoryMapper.updateById(category);
        return R.ok("更新分类成功");
    }

    @Override
    public R<String> deleteCategory(Long userId, String role, Long id) {
        checkProductManagePermission(role);
        Long childCount = categoryMapper.selectCount(
                new LambdaQueryWrapper<Category>().eq(Category::getParentId, id));
        if (childCount > 0) {
            throw new BusinessException("请先删除子分类");
        }
        categoryMapper.deleteById(id);
        return R.ok("删除分类成功");
    }

    // ==================== 商品 ====================

    @Override
    public R<Product> getProductDetail(Long productId) {
        String key = SystemConstants.REDIS_KEY_PREFIX + "product:detail:" + productId;
        Product product = productDetailTwoLevel.get(key, Product.class,
                () -> {
                    Product p = productMapper.selectById(productId);
                    if (p == null) throw new BusinessException(ResultCode.PRODUCT_NOT_EXIST);
                    return p;
                },
                300  // Redis 5分钟
        );

        try {
            redisTemplate.opsForZSet().incrementScore(
                    SystemConstants.PRODUCT_VIEWS_KEY, productId.toString(), 1);
        } catch (Exception e) {
            log.warn("Redis浏览量+1失败, productId={}", productId, e);
        }
        return R.ok(product);
    }

    @Override
    public R<List<Product>> getProductList(Long categoryId, Integer page, Integer size, String keyword) {
        // 1. 构建缓存 key
        String cid = categoryId == null ? "all" : String.valueOf(categoryId);
        String kw = keyword == null ? "" : keyword;
        String idListKey = SystemConstants.REDIS_KEY_PREFIX + "product:ids:" + cid + ":" + kw;

        // 2. 查 ID 列表（带缓存）
        @SuppressWarnings("unchecked")
        List<Long> allIds = productIdListTwoLevel.get(idListKey, List.class, () -> {
            LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
            wrapper.select(Product::getId);  // 只查 ID
            wrapper.eq(Product::getStatus, 1);

            if (categoryId != null && categoryId > 0) {
                List<Long> categoryIds = collectDescendantIds(categoryId);
                wrapper.in(Product::getCategoryId, categoryIds);
            }
            if (keyword != null && !keyword.isEmpty()) {
                wrapper.like(Product::getName, keyword);
            }
            wrapper.orderByDesc(Product::getSales);

            return productMapper.selectList(wrapper).stream()
                    .map(Product::getId)
                    .collect(Collectors.toList());
        }, 300);

        if (allIds == null || allIds.isEmpty()) {
            return R.ok(Collections.emptyList());
        }

        // 3. 手动分页
        int fromIndex = (page - 1) * size;
        if (fromIndex >= allIds.size()) {
            return R.ok(Collections.emptyList());
        }
        int toIndex = Math.min(fromIndex + size, allIds.size());
        List<Long> pageIds = allIds.subList(fromIndex, toIndex);

        // 4. 从商品详情缓存逐个取（复用场景1的缓存）
        List<Product> result = pageIds.stream().map(id ->
                productDetailTwoLevel.get(
                        SystemConstants.REDIS_KEY_PREFIX + "product:detail:" + id,
                        Product.class,
                        () -> productMapper.selectById(id),
                        300
                )
        ).collect(Collectors.toList());

        return R.ok(result);
    }

    @Override
    public R<List<Product>> getMyProducts(Long userId, Integer page, Integer size) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getSellerId, userId);
        wrapper.orderByDesc(Product::getCreateTime);
        Page<Product> productPage = new Page<>(page, size);
        productMapper.selectPage(productPage, wrapper);
        return R.ok(productPage.getRecords());
    }

    @Override
    public R<String> addProduct(Long userId, String role, Product product) {
        checkProductManagePermission(role);
        product.setSellerId(userId);
        product.setSales(0);
        // 卖家提交需要审核，管理员直接上架
        product.setStatus(SystemConstants.ROLE_ADMIN.equals(role) ? 1 : SystemConstants.PRODUCT_STATUS_PENDING);
        productMapper.insert(product);
        return R.ok(SystemConstants.ROLE_ADMIN.equals(role) ? "添加商品成功" : "添加成功，等待管理员审核");
    }

    @Override
    public R<String> updateProduct(Long userId, String role, Product product) {
        checkProductManagePermission(role);
        Product dbProduct = productMapper.selectById(product.getId());
        if (dbProduct == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_EXIST);
        }
        checkProductPermission(userId, role, dbProduct);
        product.setSellerId(dbProduct.getSellerId());
        // 卖家修改后重新进入待审核
        if (SystemConstants.ROLE_SELLER.equals(role)) {
            product.setStatus(SystemConstants.PRODUCT_STATUS_PENDING);
        }
        productMapper.updateById(product);
        productDetailTwoLevel.evict(SystemConstants.REDIS_KEY_PREFIX + "product:detail:" + product.getId());
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
        productDetailTwoLevel.evict(SystemConstants.REDIS_KEY_PREFIX + "product:detail:" + id);
        return R.ok("删除商品成功");
    }

    // ==================== 审核 ====================

    @Override
    public R<List<Product>> getPendingProducts(Long page, Long size) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, SystemConstants.PRODUCT_STATUS_PENDING)
               .orderByAsc(Product::getCreateTime);
        Page<Product> productPage = new Page<>(page, size);
        productMapper.selectPage(productPage, wrapper);
        return R.ok(productPage.getRecords());
    }

    @Override
    public R<String> reviewProduct(Long id, boolean approved) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_EXIST);
        }
        if (!SystemConstants.PRODUCT_STATUS_PENDING.equals(product.getStatus())) {
            return R.fail("该商品无需审核或已处理");
        }
        product.setStatus(approved ? 1 : 0);
        productMapper.updateById(product);
        productDetailTwoLevel.evict(SystemConstants.REDIS_KEY_PREFIX + "product:detail:" + id);
        return R.ok(approved ? "审核通过" : "已拒绝，商品已下架");
    }

    // ==================== 热门商品 ====================

    @Override
    public R<List<Product>> getHotProducts() {
        List<Product> products = hotProductsTwoLevel.get(
                SystemConstants.REDIS_KEY_PREFIX + "product:hot",
                List.class,
                () -> {
                    // 原逻辑：ZSet 取 topId → 批量查
                    try {
                        Set<Object> topIds = redisTemplate.opsForZSet()
                                .reverseRange(SystemConstants.PRODUCT_VIEWS_KEY, 0, 7);
                        if (CollUtil.isNotEmpty(topIds)) {
                            List<Long> ids = topIds.stream()
                                    .map(o -> Long.valueOf(o.toString()))
                                    .collect(Collectors.toList());
                            List<Product> list = productMapper.selectBatchIds(ids);
                            if (CollUtil.isNotEmpty(list)) {
                                list.sort((a, b) -> ids.indexOf(a.getId()) - ids.indexOf(b.getId()));
                                return list;
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Redis查询热门商品失败，降级为数据库查询", e);
                    }
                    // 降级
                    LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(Product::getStatus, 1)
                            .orderByDesc(Product::getSales)
                            .last("LIMIT 8");
                    return productMapper.selectList(wrapper);
                },
                300
        );
        return R.ok(products);
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

    /**
     * 收集分类及其所有后代子分类的 ID，让父分类筛选能命中叶子分类下的商品。
     */
    private List<Long> collectDescendantIds(Long parentId) {
        List<Category> all = categoryMapper.selectList(null);
        Map<Long, List<Category>> parentMap = all.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getParentId() == null ? 0L : c.getParentId()));
        List<Long> ids = new ArrayList<>();
        ids.add(parentId);
        collectChildren(parentId, parentMap, ids);
        return ids;
    }

    private void collectChildren(Long parentId, Map<Long, List<Category>> parentMap, List<Long> ids) {
        List<Category> children = parentMap.get(parentId);
        if (children != null) {
            for (Category child : children) {
                ids.add(child.getId());
                collectChildren(child.getId(), parentMap, ids);
            }
        }
    }

    // ==================== 库存 ====================

    /** 扣减库存，@Transactional 确保原子性；同时增加销量 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public R<String> deductStock(Long productId, Integer quantity) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_EXIST);
        }
        if (product.getStock() < quantity) {
            throw new BusinessException(ResultCode.STOCK_INSUFFICIENT);
        }

        product.setStock(product.getStock() - quantity);
        product.setSales(product.getSales() + quantity);
        productMapper.updateById(product);
        productDetailTwoLevel.evict(SystemConstants.REDIS_KEY_PREFIX + "product:detail:" + productId);
        return R.ok("扣减库存成功");
    }
}
