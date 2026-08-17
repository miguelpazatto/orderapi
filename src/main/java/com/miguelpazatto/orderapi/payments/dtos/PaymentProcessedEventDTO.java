package com.miguelpazatto.orderapi.payments.dtos;

import java.util.UUID;

public record PaymentProcessedEventDTO(
        UUID orderId,
        String paymentStatus,
        String customerEmail
) {}
