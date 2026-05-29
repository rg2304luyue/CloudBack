package org.cloudback.order.feign;

import org.cloudback.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(name = "cloud-cart", fallbackFactory = CartFeignFallbackFactory.class)
public interface CartFeignClient {

    @GetMapping("/cart/checked")
    R<List<CartItemDTO>> getCheckedItems(@RequestHeader("X-User-Id") Long userId);

    @DeleteMapping("/cart/items")
    R<String> clearCart(@RequestHeader("X-User-Id") Long userId);
}
