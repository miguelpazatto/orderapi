package com.miguelpazatto.orderapi.orders.dtos;

import com.miguelpazatto.orderapi.orders.entities.OrderItem;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponseDTO(
        UUID productId,
        String productName,
        Integer quantity,
        BigDecimal price,
        BigDecimal subtotal
) {
    public OrderItemResponseDTO(OrderItem item) {
        this(
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getQuantity(),
                item.getPrice(),
                item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
        );
    }
}
