package org.cloudback.order.feign;

import org.cloudback.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "cloud-product")
public interface ProductFeignClient {
    @PutMapping("/product/stock/deduct/{id}")
    R<String> deductStock(@PathVariable Long id, @RequestParam Integer quantity);
}
