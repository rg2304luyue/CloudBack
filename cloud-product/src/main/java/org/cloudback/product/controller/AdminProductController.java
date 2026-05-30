package org.cloudback.product.controller;

import lombok.RequiredArgsConstructor;
import org.cloudback.common.result.R;
import org.cloudback.product.dto.ReviewRequest;
import org.cloudback.product.model.entity.Product;
import org.cloudback.product.service.ProductService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    /** GET /admin/products/pending — 管理员分页获取待审核商品列表 */
    @GetMapping("/pending")
    public R<List<Product>> getPendingProducts(@RequestParam(defaultValue = "1") Long page,
                                               @RequestParam(defaultValue = "20") Long size) {
        return productService.getPendingProducts(page, size);
    }

    /** PATCH /admin/products/{id}/review — 管理员审核商品（通过→上架，拒绝→下架） */
    @PatchMapping("/{id}/review")
    public R<String> reviewProduct(@PathVariable Long id,
                                   @RequestBody ReviewRequest request) {
        return productService.reviewProduct(id, request.approved());
    }
}
