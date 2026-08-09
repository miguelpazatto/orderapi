package com.miguelpazatto.orderapi.repositories;

import com.miguelpazatto.orderapi.entities.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
}
