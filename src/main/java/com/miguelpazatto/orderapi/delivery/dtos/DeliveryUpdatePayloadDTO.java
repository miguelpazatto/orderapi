package com.miguelpazatto.orderapi.delivery.dtos;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DeliveryUpdatePayloadDTO(
        @NotNull UUID orderId,
        String trackingCode,
        @NotBlank
        @Pattern(regexp = "PENDING|DISPATCHED|IN_TRANSIT|OUT_FOR_DELIVERY|DELIVERED|FAILED")
        String status
) {
}
