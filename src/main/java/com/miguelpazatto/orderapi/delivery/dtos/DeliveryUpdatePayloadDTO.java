package com.miguelpazatto.orderapi.delivery.dtos;

import java.util.UUID;

public record DeliveryUpdatePayloadDTO(
        UUID orderId,
        String trackingCode,
        String status
) {
}
