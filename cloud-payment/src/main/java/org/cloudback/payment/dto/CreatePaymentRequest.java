package org.cloudback.payment.dto;

public record CreatePaymentRequest(String orderNo, String method) {}
