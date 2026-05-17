package org.cloudback.product.controller;

import lombok.RequiredArgsConstructor;
import org.cloudback.common.result.R;
import org.cloudback.product.model.entity.Category;
import org.cloudback.product.model.entity.Product;
import org.cloudback.product.service.ProductService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    // ========== 分类 ==========

    @GetMapping("/category")
    public R<List<Category>> getCategoryTree() {
        return productService.getCategoryTree();
    }

    @PostMapping("/category")
    public R<String> addCategory(@RequestBody Category category) {
        return productService.addCategory(category);
    }

    @PutMapping("/category")
    public R<String> updateCategory(@RequestBody Category category) {
        return productService.updateCategory(category);
    }

    @DeleteMapping("/category/{id}")
    public R<String> deleteCategory(@PathVariable Long id) {
        return productService.deleteCategory(id);
    }

    // ========== 商品 ==========

    @GetMapping("/detail/{id}")
    public R<Product> getProductDetail(@PathVariable Long id) {
        return productService.getProductDetail(id);
    }

    @GetMapping("/list")
    public R<List<Product>> getProductList(@RequestParam(required = false) Long categoryId,
                                           @RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "10") Integer size,
                                           @RequestParam(required = false) String keyword) {
        return productService.getProductList(categoryId, page, size, keyword);
    }

    @PostMapping
    public R<String> addProduct(@RequestBody Product product) {
        return productService.addProduct(product);
    }

    @PutMapping
    public R<String> updateProduct(@RequestBody Product product) {
        return productService.updateProduct(product);
    }

    @DeleteMapping("/{id}")
    public R<String> deleteProduct(@PathVariable Long id) {
        return productService.deleteProduct(id);
    }

    // ========== 库存（供订单服务内部调用）==========

    @PutMapping("/stock/deduct/{id}")
    public R<String> deductStock(@PathVariable Long id,
                                 @RequestParam Integer quantity) {
        return productService.deductStock(id, quantity);
    }
}
