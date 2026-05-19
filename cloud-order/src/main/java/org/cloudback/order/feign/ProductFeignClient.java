package org.cloudback.order.feign;

import org.cloudback.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 商品服务 Feign 客户端，用于下单时扣减库存。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@FeignClient(name = "cloud-product")
public interface ProductFeignClient {

    /** 扣减商品库存 */
    @PutMapping("/product/stock/deduct/{id}")
    R<String> deductStock(@PathVariable Long id, @RequestParam Integer quantity);

    /** 获取商品基本信息（名称和图片） */
    @GetMapping("/product/detail/{id}")
    R<ProductDTO> getProductDetail(@PathVariable Long id);
}
