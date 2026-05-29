package org.cloudback.product.controller;

import lombok.RequiredArgsConstructor;
import org.cloudback.common.result.R;
import org.cloudback.product.dto.ProductRequest;
import org.cloudback.product.model.entity.Product;
import org.cloudback.product.service.ProductService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/seller/products")
@RequiredArgsConstructor
public class SellerProductController {

    private final ProductService productService;

    @GetMapping("/mine")
    public R<List<Product>> getMyProducts(@RequestHeader("X-User-Id") Long userId,
                                          @RequestParam(defaultValue = "1") Integer page,
                                          @RequestParam(defaultValue = "20") Integer size) {
        return productService.getMyProducts(userId, page, size);
    }

    @PostMapping
    public R<String> addProduct(@RequestHeader("X-User-Id") Long userId,
                                @RequestHeader("X-User-Role") String role,
                                @RequestBody ProductRequest request) {
        return productService.addProduct(userId, role, request);
    }

    @PutMapping("/{id}")
    public R<String> updateProduct(@RequestHeader("X-User-Id") Long userId,
                                   @RequestHeader("X-User-Role") String role,
                                   @PathVariable Long id,
                                   @RequestBody ProductRequest request) {
        return productService.updateProduct(userId, role, id, request);
    }

    @DeleteMapping("/{id}")
    public R<String> deleteProduct(@RequestHeader("X-User-Id") Long userId,
                                   @RequestHeader("X-User-Role") String role,
                                   @PathVariable Long id) {
        return productService.deleteProduct(userId, role, id);
    }
}
