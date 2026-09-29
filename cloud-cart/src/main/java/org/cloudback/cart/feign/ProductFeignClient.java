package org.cloudback.cart.feign;

import org.cloudback.cart.dto.CartItem;
import org.cloudback.cart.dto.ProductInfo;
import org.cloudback.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "cloud-product", fallbackFactory = ProductFeignFallbackFactory.class)
public interface ProductFeignClient {

    @GetMapping("/products/{id}")
    R<CartItem> getProductDetail(@PathVariable Long id);

    /** 批量查询商品实时信息（价格/状态/库存），内部接口 */
    @PostMapping("/products/internal/batch")
    R<List<ProductInfo>> getProductsByIds(@RequestBody List<Long> ids);
}
