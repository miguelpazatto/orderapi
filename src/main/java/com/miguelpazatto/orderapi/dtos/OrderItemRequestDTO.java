package com.miguelpazatto.orderapi.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemRequestDTO(
        @NotNull(message = "O ID do pedido não pode ser nulo")
        UUID orderId,

        @NotNull(message = "O ID do produto não pode ser nulo")
        UUID productId,

        @NotNull(message = "A quantidade do produto não pode ser nula")
        @Positive(message = "A quantidade deve ser maior que zero")
        Integer quantity,

        @NotNull(message = "O preço do item do pedido não pode ser nulo")
        BigDecimal price) {
}
