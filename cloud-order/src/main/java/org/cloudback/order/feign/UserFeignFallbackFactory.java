package org.cloudback.order.feign;

import lombok.extern.slf4j.Slf4j;
import org.cloudback.common.result.R;
import org.cloudback.common.result.ResultCode;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class UserFeignFallbackFactory implements FallbackFactory<UserFeignClient> {
    @Override
    public UserFeignClient create(Throwable cause) {
        log.error("CartFeignClient 调用失败，触发熔断降级", cause);
        return new UserFeignClient() {
            @Override
            public org.cloudback.common.result.R<Map<String, Object>> getAddressById(@RequestHeader("X-User-Id") Long userId,
                                                                                     @PathVariable Long addressId) {
                return R.fail(ResultCode.SERVICE_UNAVAILABLE);
            }
        };
    }
}
