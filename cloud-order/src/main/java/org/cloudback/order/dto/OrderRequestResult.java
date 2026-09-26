package org.cloudback.order.dto;

public record OrderRequestResult(String status, String orderId, String orderNo, String message) {}
