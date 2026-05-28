package org.cloudback.product.controller;

import lombok.RequiredArgsConstructor;
import org.cloudback.common.result.R;
import org.cloudback.product.model.entity.Category;
import org.cloudback.product.service.ProductService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final ProductService productService;

    @GetMapping
    public R<List<Category>> getCategoryTree() {
        return productService.getCategoryTree();
    }

    @PostMapping
    public R<String> addCategory(@RequestHeader("X-User-Id") Long userId,
                                 @RequestHeader("X-User-Role") String role,
                                 @RequestBody Category category) {
        return productService.addCategory(userId, role, category);
    }

    @PutMapping("/{id}")
    public R<String> updateCategory(@RequestHeader("X-User-Id") Long userId,
                                    @RequestHeader("X-User-Role") String role,
                                    @PathVariable Long id,
                                    @RequestBody Category category) {
        category.setId(id);
        return productService.updateCategory(userId, role, category);
    }

    @DeleteMapping("/{id}")
    public R<String> deleteCategory(@RequestHeader("X-User-Id") Long userId,
                                    @RequestHeader("X-User-Role") String role,
                                    @PathVariable Long id) {
        return productService.deleteCategory(userId, role, id);
    }
}
