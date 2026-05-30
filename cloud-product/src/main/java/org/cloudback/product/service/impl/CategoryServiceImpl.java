package org.cloudback.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.exception.BusinessException;
import org.cloudback.common.result.R;
import org.cloudback.common.result.ResultCode;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.product.mapper.CategoryMapper;
import org.cloudback.product.model.entity.Category;
import org.cloudback.product.service.CategoryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;

    /** 获取完整分类树：查全部分类 → 按 parentId 分组 → 递归填充子分类 */
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

    /** 递归填充子分类，将扁平列表组装为树形结构 */
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
    public R<String> addCategory(String role, Category category) {
        if (!SystemConstants.ROLE_SELLER.equals(role) && !SystemConstants.ROLE_ADMIN.equals(role)) {
            throw new BusinessException(ResultCode.SELLER_ONLY);
        }
        categoryMapper.insert(category);
        return R.ok("添加分类成功");
    }

    @Override
    public R<String> updateCategory(String role, Category category) {
        if (!SystemConstants.ROLE_SELLER.equals(role) && !SystemConstants.ROLE_ADMIN.equals(role)) {
            throw new BusinessException(ResultCode.SELLER_ONLY);
        }
        Category dbCategory = categoryMapper.selectById(category.getId());
        if (dbCategory == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "分类不存在");
        }
        categoryMapper.updateById(category);
        return R.ok("更新分类成功");
    }

    @Override
    public R<String> deleteCategory(String role, Long id) {
        if (!SystemConstants.ROLE_SELLER.equals(role) && !SystemConstants.ROLE_ADMIN.equals(role)) {
            throw new BusinessException(ResultCode.SELLER_ONLY);
        }
        Long childCount = categoryMapper.selectCount(
                new LambdaQueryWrapper<Category>().eq(Category::getParentId, id));
        if (childCount > 0) {
            throw new BusinessException("请先删除子分类");
        }
        categoryMapper.deleteById(id);
        return R.ok("删除分类成功");
    }

    @Override
    public List<Long> collectDescendantIds(Long parentId) {
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
}

