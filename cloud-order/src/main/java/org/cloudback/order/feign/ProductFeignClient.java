package org.cloudback.order.feign;

import org.cloudback.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品服务 Feign 客户端，用于下单时扣减库存。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@FeignClient(name = "cloud-product")
public interface ProductFeignClient {

    /** POST /products/{id}/stock/deduct — 原子扣减库存（下单时调用，WHERE stock>=quantity 防超卖） */
    @PostMapping("/products/{id}/stock/deduct")
    R<String> deductStock(@PathVariable Long id, @RequestParam Integer quantity);

    /** POST /products/{id}/stock/restore — 回滚库存（取消订单/支付超时时补偿调用） */
    @PostMapping("/products/{id}/stock/restore")
    R<String> restoreStock(@PathVariable Long id, @RequestParam Integer quantity);

    /** GET /products/{id} — 获取商品基本信息（名称和图片，下单时补全缺失的商品快照） */
    @GetMapping("/products/{id}")
    R<ProductDTO> getProductDetail(@PathVariable Long id);

    /** GET /products/seller/{sellerId} — 获取卖家商品 ID 列表（卖家查订单时反查关联） */
    @GetMapping("/products/seller/{sellerId}")
    R<List<ProductDTO>> getProductsBySellerId(@PathVariable Long sellerId);
}
