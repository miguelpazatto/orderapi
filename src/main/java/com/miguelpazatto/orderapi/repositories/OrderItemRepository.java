package com.miguelpazatto.orderapi.repositories;

import com.miguelpazatto.orderapi.entities.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
}
