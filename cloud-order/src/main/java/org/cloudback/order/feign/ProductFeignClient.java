package org.cloudback.order.feign;

import org.cloudback.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 商品服务 Feign 客户端，用于下单时扣减库存。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@FeignClient(name = "cloud-product", fallbackFactory = ProductFeignFallbackFactory.class)
public interface ProductFeignClient {

    /** 扣减商品库存 */
    @PutMapping("/products/stock/deduct/{id}")
    R<String> deductStock(@PathVariable Long id, @RequestParam Integer quantity);

    /** 回滚商品库存（取消订单/支付超时） */
    @PutMapping("/products/stock/restore/{id}")
    R<String> restoreStock(@PathVariable Long id, @RequestParam Integer quantity);

    /** 获取商品基本信息（名称和图片） */
    @GetMapping("/products/{id}")
    R<ProductDTO> getProductDetail(@PathVariable Long id);

    /** 获取卖家的所有商品（用于卖家订单查询） */
    @GetMapping("/products/seller/{sellerId}")
    R<List<ProductDTO>> getProductsBySellerId(@PathVariable Long sellerId);
}
