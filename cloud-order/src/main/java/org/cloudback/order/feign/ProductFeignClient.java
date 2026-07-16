package org.cloudback.order.feign;

import org.cloudback.common.constant.SystemConstants;
import org.cloudback.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "cloud-product")
public interface ProductFeignClient {

    @PostMapping("/products/{id}/stock/deduct")
    R<String> deductStock(@PathVariable Long id,
                          @RequestParam Integer quantity,
                          @RequestHeader(SystemConstants.INTERNAL_TOKEN_HEADER) String internalToken);

    @PostMapping("/products/{id}/stock/restore")
    R<String> restoreStock(@PathVariable Long id,
                           @RequestParam Integer quantity,
                           @RequestHeader(SystemConstants.INTERNAL_TOKEN_HEADER) String internalToken);

    @GetMapping("/products/{id}")
    R<ProductDTO> getProductDetail(@PathVariable Long id);

    @GetMapping("/products/seller")
    R<List<ProductDTO>> getProductsBySellerId(@RequestParam Long sellerId);
}
