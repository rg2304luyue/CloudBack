package org.cloudback.order.feign;

import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.result.R;
import org.cloudback.common.result.ResultCode;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@Slf4j
@Component
public class CartFeignFallbackFactory implements FallbackFactory<CartFeignClient> {
    @Override
    public CartFeignClient create(Throwable cause) {
        log.error("CartFeignClient 调用失败，触发熔断降级", cause);
        return new CartFeignClient() {
            @Override
            public R<List<CartItemDTO>> getCheckedItems(@RequestHeader("X-User-Id") Long userId) {
                return R.fail(ResultCode.SERVICE_UNAVAILABLE);
            }

            @Override
            public R<String> clearCart(@RequestHeader("X-User-Id") Long userId) {
                return R.fail(ResultCode.SERVICE_UNAVAILABLE);
            }
        };
    }
}
