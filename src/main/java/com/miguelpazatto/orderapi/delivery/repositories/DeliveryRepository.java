package com.miguelpazatto.orderapi.delivery.repositories;

import com.miguelpazatto.orderapi.delivery.entities.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {
}
