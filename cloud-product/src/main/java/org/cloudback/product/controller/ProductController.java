package org.cloudback.product.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.common.exception.BusinessException;
import org.cloudback.common.result.R;
import org.cloudback.common.result.ResultCode;
import org.cloudback.common.service.FileService;
import org.cloudback.product.model.entity.Product;
import org.cloudback.product.service.ProductService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final FileService fileService;

    @Value("${cloud.internal.token}")
    private String internalToken;

    @GetMapping("/{id}")
    public R<Product> getProductDetail(@PathVariable Long id) {
        return productService.getProductDetail(id);
    }

    @GetMapping("/hot")
    public R<List<Product>> getHotProducts() {
        return productService.getHotProducts();
    }

    @GetMapping
    public R<List<Product>> getProductList(@RequestParam(required = false) Long categoryId,
                                           @RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "10") Integer size,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String sortBy) {
        return productService.getProductList(categoryId, page, size, keyword, sortBy);
    }

    @GetMapping("/search")
    public R<List<Product>> search(@RequestParam String keyword,
                                   @RequestParam(required = false) Long categoryId,
                                   @RequestParam(defaultValue = "1") Integer page,
                                   @RequestParam(defaultValue = "10") Integer size) {
        return productService.search(keyword, categoryId, page, size);
    }

    @GetMapping("/suggest")
    public R<List<String>> suggest(@RequestParam String keyword,
                                   @RequestParam(defaultValue = "10") int limit) {
        return productService.suggest(keyword, limit);
    }

    @PostMapping("/{id}/stock/deduct")
    public R<String> deductStock(@PathVariable Long id,
                                 @RequestParam Integer quantity,
                                 @RequestHeader(SystemConstants.INTERNAL_TOKEN_HEADER) String token) {
        checkInternalToken(token);
        return productService.deductStock(id, quantity);
    }

    @PostMapping("/{id}/stock/restore")
    public R<String> restoreStock(@PathVariable Long id,
                                  @RequestParam Integer quantity,
                                  @RequestHeader(SystemConstants.INTERNAL_TOKEN_HEADER) String token) {
        checkInternalToken(token);
        return productService.restoreStock(id, quantity);
    }

    @GetMapping("/seller")
    public R<List<Product>> getProductsBySellerId(@RequestParam Long sellerId) {
        return productService.getProductsBySellerId(sellerId);
    }

    @PostMapping("/upload")
    public R<String> uploadImage(@RequestHeader(SystemConstants.USER_ROLE_HEADER) String role,
                                 @RequestParam("file") MultipartFile file) {
        if (!SystemConstants.ROLE_SELLER.equals(role) && !SystemConstants.ROLE_ADMIN.equals(role)) {
            throw new BusinessException(ResultCode.SELLER_ONLY);
        }
        String url = fileService.upload(file, "product");
        return R.ok(url);
    }

    private void checkInternalToken(String token) {
        if (!internalToken.equals(token)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }
}
