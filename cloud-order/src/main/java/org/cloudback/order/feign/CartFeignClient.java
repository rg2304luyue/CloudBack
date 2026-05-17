package org.cloudback.order.feign;

import org.cloudback.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;
import java.util.Map;

@FeignClient(name = "cloud-cart")
public interface CartFeignClient {
    @GetMapping("/cart/checked")
    R<List<Map<String, Object>>> getCheckedItems();

    @DeleteMapping("/cart/clear")
    R<String> clearCart();
}
