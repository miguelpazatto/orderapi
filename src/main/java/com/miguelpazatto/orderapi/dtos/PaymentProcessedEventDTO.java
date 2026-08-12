package com.miguelpazatto.orderapi.dtos;

import com.miguelpazatto.orderapi.entities.Payment;
import com.miguelpazatto.orderapi.entities.enums.PaymentStatus;

import java.util.UUID;

public record PaymentProcessedEventDTO(
        UUID orderId,
        PaymentStatus paymentStatus
) {

    public PaymentProcessedEventDTO(Payment entity) {
        this(
                entity.getOrderId(),
                entity.getPaymentStatus()
        );
    }

}
