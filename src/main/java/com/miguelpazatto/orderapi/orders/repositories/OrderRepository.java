package com.miguelpazatto.orderapi.orders.repositories;

import com.miguelpazatto.orderapi.orders.entities.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
}
