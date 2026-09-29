package org.cloudback.product.controller;

import jakarta.validation.constraints.Size;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

@Slf4j
@Validated
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final FileService fileService;

    @Value("${cloud.internal.token:}")
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
                                 @RequestParam String orderNo,
                                 @RequestHeader(SystemConstants.INTERNAL_TOKEN_HEADER) String token) {
        checkInternalToken(token);
        return productService.deductStock(id, quantity, orderNo);
    }

    @PostMapping("/{id}/stock/restore")
    public R<String> restoreStock(@PathVariable Long id,
                                  @RequestParam Integer quantity,
                                  @RequestParam String orderNo,
                                  @RequestHeader(SystemConstants.INTERNAL_TOKEN_HEADER) String token) {
        checkInternalToken(token);
        return productService.restoreStock(id, quantity, orderNo);
    }

    @GetMapping("/seller")
    public R<List<Product>> getProductsBySellerId(@RequestParam Long sellerId,
                                                  @RequestHeader(value = SystemConstants.INTERNAL_TOKEN_HEADER, required = false) String token) {
        checkInternalToken(token);
        return productService.getProductsBySellerId(sellerId);
    }

    /** 内部批量查询商品（供购物车价格刷新等场景），由 common 的 X-Internal-Token 过滤器保护 */
    @PostMapping("/internal/batch")
    public R<List<Product>> getProductsByIds(@RequestBody @Size(max = 100, message = "单次批量查询不能超过100个商品") List<Long> ids) {
        return productService.getProductsByIds(ids);
    }

    /** 管理员上下架商品 */
    @PutMapping("/admin/{id}/status")
    public R<String> updateProductStatus(@RequestHeader(SystemConstants.USER_ROLE_HEADER) String role,
                                         @PathVariable Long id,
                                         @RequestBody Map<String, Integer> body) {
        if (!SystemConstants.ROLE_ADMIN.equals(role)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        Integer status = body == null ? null : body.get("status");
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "status 只能为 0(下架) 或 1(上架)");
        }
        return productService.updateProductStatus(id, status);
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

    /** 常量时间比较内部令牌，防止时序侧信道 */
    private void checkInternalToken(String token) {
        byte[] expected = internalToken.getBytes(StandardCharsets.UTF_8);
        byte[] actual = (token == null ? "" : token).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }
}
