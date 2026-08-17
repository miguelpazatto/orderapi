package com.miguelpazatto.orderapi.customers.repositories;

import com.miguelpazatto.orderapi.customers.entities.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    boolean existsByEmail(String email);

    List<Customer> findByActiveTrue();
}
