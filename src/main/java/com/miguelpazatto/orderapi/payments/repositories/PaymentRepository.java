package com.miguelpazatto.orderapi.payments.repositories;

import com.miguelpazatto.orderapi.payments.entities.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
}
