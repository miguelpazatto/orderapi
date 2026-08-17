package com.miguelpazatto.orderapi.orders.dtos;

import java.util.UUID;

public record OrderDispatchedEventDTO(
        UUID orderId
        // futuramente incluir endereço
) {
}
