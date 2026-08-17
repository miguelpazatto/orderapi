package com.miguelpazatto.orderapi.orders.dtos;

import com.miguelpazatto.orderapi.orders.entities.Order;
import com.miguelpazatto.orderapi.orders.entities.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponseDTO(
        UUID id,
        Instant purchaseMoment,
        OrderStatus orderStatus,
        BigDecimal totalPrice,
        String customerEmail,
        List<OrderItemResponseDTO> orderItemList
) {
    public OrderResponseDTO(Order entity) {
        this(
                entity.getId(),
                entity.getPurchaseMoment(),
                entity.getOrderStatus(),
                entity.getTotalPrice(),
                entity.getCustomer().getEmail(),
                entity.getOrderItemList().stream().map(OrderItemResponseDTO::new).toList()
        );
    }
}