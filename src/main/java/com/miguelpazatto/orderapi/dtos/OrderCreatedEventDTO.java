package com.miguelpazatto.orderapi.dtos;

import com.miguelpazatto.orderapi.entities.Order;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderCreatedEventDTO(
        UUID orderId,
        UUID customerId,
        BigDecimal totalPrice
) {

    public OrderCreatedEventDTO(Order entity) {
        this(
                entity.getId(),
                entity.getCustomer().getId(),
                entity.getTotalPrice()
        );
    }

}
