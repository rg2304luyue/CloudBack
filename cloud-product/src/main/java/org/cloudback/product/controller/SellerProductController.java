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

    /** GET /seller/products/mine — 卖家分页查看自己的商品列表 */
    @GetMapping("/mine")
    public R<List<Product>> getMyProducts(@RequestHeader("X-User-Id") Long userId,
                                          @RequestParam(defaultValue = "1") Integer page,
                                          @RequestParam(defaultValue = "20") Integer size) {
        return productService.getMyProducts(userId, page, size);
    }

    /** POST /seller/products — 卖家添加商品，需等待管理员审核 */
    @PostMapping
    public R<String> addProduct(@RequestHeader("X-User-Id") Long userId,
                                @RequestHeader("X-User-Role") String role,
                                @RequestBody ProductRequest request) {
        return productService.addProduct(userId, role, request);
    }

    /** PUT /seller/products/{id} — 卖家修改商品，修改后重新进入待审核状态 */
    @PutMapping("/{id}")
    public R<String> updateProduct(@RequestHeader("X-User-Id") Long userId,
                                   @RequestHeader("X-User-Role") String role,
                                   @PathVariable Long id,
                                   @RequestBody ProductRequest request) {
        return productService.updateProduct(userId, role, id, request);
    }

    /** DELETE /seller/products/{id} — 卖家逻辑删除自己的商品 */
    @DeleteMapping("/{id}")
    public R<String> deleteProduct(@RequestHeader("X-User-Id") Long userId,
                                   @RequestHeader("X-User-Role") String role,
                                   @PathVariable Long id) {
        return productService.deleteProduct(userId, role, id);
    }
}
