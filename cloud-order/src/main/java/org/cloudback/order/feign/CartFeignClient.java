package org.cloudback.order.feign;

import org.cloudback.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;
import java.util.Map;

/**
 * 购物车服务 Feign 客户端，用于获取已勾选商品和清空购物车。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@FeignClient(name = "cloud-cart")
public interface CartFeignClient {

    /** 获取已勾选的购物车商品列表 */
    @GetMapping("/cart/checked")
    R<List<Map<String, Object>>> getCheckedItems();

    /** 清空购物车 */
    @DeleteMapping("/cart/clear")
    R<String> clearCart();
}
