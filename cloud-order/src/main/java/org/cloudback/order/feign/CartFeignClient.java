package org.cloudback.order.feign;

import org.cloudback.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(name = "cloud-cart")
public interface CartFeignClient {

    /** GET /cart/checked — 获取用户购物车中已勾选的商品列表（下单时调用） */
    @GetMapping("/cart/checked")
    R<List<CartItemDTO>> getCheckedItems(@RequestHeader("X-User-Id") Long userId);

    /** DELETE /cart/items — 清空用户购物车（下单成功后调用） */
    @DeleteMapping("/cart/items")
    R<String> clearCart(@RequestHeader("X-User-Id") Long userId);
}
