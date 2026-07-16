package org.cloudback.product.controller;

import lombok.RequiredArgsConstructor;
import org.cloudback.common.constant.SystemConstants;
import org.cloudback.common.exception.BusinessException;
import org.cloudback.common.result.R;
import org.cloudback.common.result.ResultCode;
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
    public R<List<Product>> getPendingProducts(@RequestHeader(SystemConstants.USER_ROLE_HEADER) String role,
                                               @RequestParam(defaultValue = "1") Integer page,
                                               @RequestParam(defaultValue = "20") Integer size) {
        checkAdmin(role);
        return productService.getPendingProducts((long) page, (long) size);
    }

    @PatchMapping("/{id}/review")
    public R<String> reviewProduct(@RequestHeader(SystemConstants.USER_ROLE_HEADER) String role,
                                   @PathVariable Long id,
                                   @RequestBody ReviewRequest request) {
        checkAdmin(role);
        return productService.reviewProduct(id, request.approved());
    }

    private void checkAdmin(String role) {
        if (!SystemConstants.ROLE_ADMIN.equals(role)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }
}
