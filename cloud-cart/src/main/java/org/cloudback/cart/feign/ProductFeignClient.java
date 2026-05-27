package org.cloudback.cart.feign;

import org.cloudback.cart.dto.CartItem;
import org.cloudback.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "cloud-product", fallbackFactory = ProductFeignFallbackFactory.class)
public interface ProductFeignClient {

    @GetMapping("/product/detail/{id}")
    R<CartItem> getProductDetail(@PathVariable Long id);
}
