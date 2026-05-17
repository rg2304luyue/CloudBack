package org.cloudback.order.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/**
 * 用户服务 Feign 客户端，用于下单时查询收货地址。
 *
 * @author CloudBack
 * @since 2025-05-17
 */
@FeignClient(name = "cloud-user")
public interface UserFeignClient {

    /** 根据地址 ID 查询收货地址详情 */
    @GetMapping("/user/address/{addressId}")
    org.cloudback.common.result.R<Map<String, Object>> getAddressById(@RequestHeader("X-User-Id") Long userId,
                                                                       @PathVariable Long addressId);
}
