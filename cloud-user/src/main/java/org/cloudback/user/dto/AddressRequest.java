package org.cloudback.user.dto;

public record AddressRequest(
        String receiverName,
        String phone,
        String province,
        String city,
        String district,
        String detail,
        Integer isDefault
) {}

