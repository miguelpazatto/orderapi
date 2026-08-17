package com.miguelpazatto.orderapi.orders.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record OrderItemRequestDTO(
        @NotNull(message = "{item.productId.notNull}")
        UUID productId,

        @NotNull(message = "{item.quantity.notNull}")
        @Positive(message = "{item.quantity.positive}")
        @Max(value = 999, message = "{item.quantity.max}")
        Integer quantity
) {
}
