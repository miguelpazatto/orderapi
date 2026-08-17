package com.miguelpazatto.orderapi.delivery.dtos;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DeliveryRequestDTO(

        @NotNull(message = "O ID de referência do pedido não pode ser nulo para a transportadora.")
        UUID orderId

) {
}
