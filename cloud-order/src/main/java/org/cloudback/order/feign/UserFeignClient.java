package org.cloudback.order.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "cloud-user")
public interface UserFeignClient {
    @GetMapping("/user/address/{addressId}")
    org.cloudback.common.result.R<Map<String, Object>> getAddressById(@RequestHeader("X-User-Id") Long userId,
                                                                      @PathVariable Long addressId);
}
