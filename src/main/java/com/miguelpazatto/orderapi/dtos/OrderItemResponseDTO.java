package com.miguelpazatto.orderapi.dtos;

import com.miguelpazatto.orderapi.entities.OrderItem;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponseDTO(
        UUID id,
        UUID orderId,
        UUID productId,
        Integer quantity,
        BigDecimal price) {

    public OrderItemResponseDTO(OrderItem entity) {
        this(
                entity.getId(),
                entity.getOrder().getId(),
                entity.getProduct().getId(),
                entity.getQuantity(),
                entity.getPrice()
        );
    }
}
