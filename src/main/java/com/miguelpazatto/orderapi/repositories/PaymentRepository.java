package com.miguelpazatto.orderapi.repositories;

import com.miguelpazatto.orderapi.entities.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
}
