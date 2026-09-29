package org.cloudback.cart.feign;

import lombok.extern.slf4j.Slf4j;
import org.cloudback.cart.dto.CartItem;
import org.cloudback.cart.dto.ProductInfo;
import org.cloudback.common.result.R;
import org.cloudback.common.result.ResultCode;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Slf4j
@Component
public class ProductFeignFallbackFactory implements FallbackFactory<ProductFeignClient> {
    @Override
    public ProductFeignClient create(Throwable cause) {
        log.error("ProductFeignClient 调用失败，触发熔断降级", cause);
        return new ProductFeignClient() {
            @Override
            public R<CartItem> getProductDetail(@PathVariable Long id) {
                return R.fail(ResultCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public R<List<ProductInfo>> getProductsByIds(List<Long> ids) {
                return R.fail(ResultCode.SERVICE_UNAVAILABLE);
            }
        };
    }
}
