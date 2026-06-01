package org.cloudback.product.controller;

import lombok.RequiredArgsConstructor;
import org.cloudback.common.result.R;
import org.cloudback.product.model.entity.Category;
import org.cloudback.product.service.CategoryService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /** GET /categories — 获取完整分类树（递归嵌套子分类） */
    @GetMapping
    public R<List<Category>> getCategoryTree() {
        return categoryService.getCategoryTree();
    }

    /** POST /categories — 添加分类（卖家/管理员） */
    @PostMapping
    public R<String> addCategory(@RequestHeader("X-User-Role") String role,
                                 @RequestBody Category category) {
        return categoryService.addCategory(role, category);
    }

    /** PUT /categories/{id} — 修改分类（卖家/管理员） */
    @PutMapping("/{id}")
    public R<String> updateCategory(@RequestHeader("X-User-Role") String role,
                                    @PathVariable Long id,
                                    @RequestBody Category category) {
        // 不修改 Request Body，创建新对象传入 Service
        Category updateCategory = new Category();
        updateCategory.setId(id);
        updateCategory.setName(category.getName());
        updateCategory.setParentId(category.getParentId());
        updateCategory.setIcon(category.getIcon());
        updateCategory.setSort(category.getSort());
        return categoryService.updateCategory(role, updateCategory);
    }

    /** DELETE /categories/{id} — 删除分类，有子分类时拒绝删除 */
    @DeleteMapping("/{id}")
    public R<String> deleteCategory(@RequestHeader("X-User-Role") String role,
                                    @PathVariable Long id) {
        return categoryService.deleteCategory(role, id);
    }
}
