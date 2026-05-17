package org.cloudback.cart.feign;

import org.cloudback.cart.dto.CartItem;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 商品服务 Feign 客户端，用于查询商品信息后加入购物车。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@FeignClient(name = "cloud-product")
public interface ProductFeignClient {

    /** 根据商品 ID 查询商品详情 */
    @GetMapping("/product/detail/{id}")
    org.cloudback.common.result.R<CartItem> getProductDetail(@PathVariable Long id);
}
