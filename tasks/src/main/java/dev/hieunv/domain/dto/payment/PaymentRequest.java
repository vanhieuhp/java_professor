package dev.hieunv.domain.dto.payment;

public record PaymentRequest(String orderId, String customerId, int amount, String checksum) {}