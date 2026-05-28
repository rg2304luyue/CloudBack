package org.cloudback.order.feign;

import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.result.R;
import org.cloudback.common.result.ResultCode;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProductFeignFallbackFactory implements FallbackFactory<ProductFeignClient> {
    @Override
    public ProductFeignClient create(Throwable cause) {
        log.error("ProductFeignClient 调用失败，触发熔断降级", cause);
        return new ProductFeignClient() {
            @Override
            public R<ProductDTO> getProductDetail(Long id) {
                return R.fail(ResultCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public R<String> deductStock(Long id, Integer quantity) {
                return R.fail(ResultCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public R<String> restoreStock(Long id, Integer quantity) {
                return R.fail(ResultCode.SERVICE_UNAVAILABLE);
            }
        };
    }
}
