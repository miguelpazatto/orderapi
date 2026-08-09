package com.miguelpazatto.orderapi.dtos;

import com.miguelpazatto.orderapi.entities.Order;
import com.miguelpazatto.orderapi.entities.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponseDTO(
        UUID id,
        Instant purchaseMoment,
        OrderStatus orderStatus,
        BigDecimal totalPrice,
        UUID customerId,
        List<OrderItemResponseDTO> orderItemList
) {
    public OrderResponseDTO(Order entity) {
        this(
                entity.getId(),
                entity.getPurchaseMoment(),
                entity.getOrderStatus(),
                entity.getTotalPrice(),
                entity.getCustomer().getId(),
                entity.getOrderItemList().stream().map(OrderItemResponseDTO::new).toList()
        );
    }
}