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

    @GetMapping("/pending")
    public R<List<Product>> getPendingProducts(@RequestParam(defaultValue = "1") Long page,
                                               @RequestParam(defaultValue = "20") Long size) {
        return productService.getPendingProducts(page, size);
    }

    @PatchMapping("/{id}/review")
    public R<String> reviewProduct(@PathVariable Long id,
                                   @RequestBody ReviewRequest request) {
        return productService.reviewProduct(id, request.approved());
    }
}
