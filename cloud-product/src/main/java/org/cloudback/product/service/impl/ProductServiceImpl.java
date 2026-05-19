package org.cloudback.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.common.exception.BusinessException;
import org.cloudback.common.result.R;
import org.cloudback.common.result.ResultCode;
import org.cloudback.product.mapper.CategoryMapper;
import org.cloudback.product.mapper.ProductMapper;
import org.cloudback.product.model.entity.Category;
import org.cloudback.product.model.entity.Product;
import org.cloudback.product.service.ProductService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品服务实现，处理分类树、商品 CRUD、分页搜索、库存扣减。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;

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
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_EXIST);
        }
        return R.ok(product);
    }

    @Override
    public R<List<Product>> getProductList(Long categoryId, Integer page, Integer size, String keyword) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(Product::getStatus, 1);

        if (categoryId != null && categoryId > 0) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }

        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(Product::getName, keyword);
        }

        wrapper.orderByDesc(Product::getSales);

        Page<Product> productPage = new Page<>(page, size);
        productPage = productMapper.selectPage(productPage, wrapper);
        return R.ok(productPage.getRecords());
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
        product.setStatus(1);
        productMapper.insert(product);
        return R.ok("添加商品成功");
    }

    @Override
    public R<String> updateProduct(Long userId, String role, Product product) {
        checkProductManagePermission(role);
        Product dbProduct = productMapper.selectById(product.getId());
        if (dbProduct == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_EXIST);
        }
        checkProductPermission(userId, role, dbProduct);
        product.setSellerId(dbProduct.getSellerId()); // 不允许修改卖家
        productMapper.updateById(product);
        return R.ok("更新商品成功");
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
        return R.ok("删除商品成功");
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
        return R.ok("扣减库存成功");
    }
}
