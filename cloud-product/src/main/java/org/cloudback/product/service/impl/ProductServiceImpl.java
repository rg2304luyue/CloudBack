package org.cloudback.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;

    // ========== 分类 ==========

    @Override
    public R<List<Category>> getCategoryTree() {
        // 查询所有分类
        List<Category> allCategories = categoryMapper.selectList(null);

        // 按parentId分组
        Map<Long, List<Category>> parentMap = allCategories.stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() == null ? 0L : c.getParentId()));

        // 取顶级分类
        List<Category> roots = parentMap.getOrDefault(0L, new ArrayList<>());

        // 递归组装子分类
        for (Category root : roots) {
            fillChildren(root, parentMap);
        }
        return R.ok(roots);
    }

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
    public R<String> addCategory(Category category) {
        categoryMapper.insert(category);
        return R.ok("添加分类成功");
    }

    @Override
    public R<String> updateCategory(Category category) {
        Category dbCategory = categoryMapper.selectById(category.getId());
        if (dbCategory == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "分类不存在");
        }
        categoryMapper.updateById(category);
        return R.ok("更新分类成功");
    }

    @Override
    public R<String> deleteCategory(Long id) {
        return null;
    }

    // ========== 商品 ==========

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

        // 状态为"上架"的才展示
        wrapper.eq(Product::getStatus, 1);

        // 分类筛选（支持查所有子分类下的商品需额外处理，这里先做简单筛选）
        if (categoryId != null && categoryId > 0) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }

        // 关键词搜索
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(Product::getName, keyword);
        }

        // 按销量降序
        wrapper.orderByDesc(Product::getSales);

        Page<Product> productPage = new Page<>(page, size);
        productPage = productMapper.selectPage(productPage, wrapper);
        return R.ok(productPage.getRecords());
    }

    @Override
    public R<String> addProduct(Product product) {
        return null;
    }

    @Override
    public R<String> updateProduct(Product product) {
        product.setSales(0);
        product.setStatus(1);
        productMapper.insert(product);
        return R.ok("添加商品成功");
    }

    @Override
    public R<String> deleteProduct(Long id) {
        Product dbProduct = productMapper.selectById(id);
        if (dbProduct == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_EXIST);
        }
        productMapper.updateById(dbProduct);
        return R.ok("更新商品成功");
    }

    // ========== 库存 ==========

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
