package com.miguelpazatto.orderapi.orders.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record OrderRequestDTO(
        @NotNull(message = "O ID do cliente é obrigatório")
        UUID customerId,

        @NotEmpty(message = "O pedido deve conter pelo menos um item")
        @NotNull(message = "A lista de itens não pode ser nula")
        @Valid
        List<OrderItemRequestDTO> items
) {
}
