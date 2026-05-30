package org.cloudback.product.service;

import org.cloudback.common.result.R;
import org.cloudback.product.model.entity.Category;
import java.util.List;

public interface CategoryService {

    /** 获取完整分类树（递归嵌套子分类），用于前端分类导航 */
    R<List<Category>> getCategoryTree();

    /** 添加分类，仅卖家和管理员可操作 */
    R<String> addCategory(String role, Category category);

    /** 修改分类，仅卖家和管理员可操作 */
    R<String> updateCategory(String role, Category category);

    /** 删除分类，有子分类时不允许删除 */
    R<String> deleteCategory(String role, Long id);

    /** 收集分类及其所有后代子分类的 ID，让父分类筛选能命中叶子分类下的商品 */
    List<Long> collectDescendantIds(Long parentId);
}

