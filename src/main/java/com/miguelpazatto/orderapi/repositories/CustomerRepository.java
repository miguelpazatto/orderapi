package com.miguelpazatto.orderapi.repositories;

import com.miguelpazatto.orderapi.entities.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    boolean existsByEmail(String email);

    List<Customer> findByActiveTrue();
}
