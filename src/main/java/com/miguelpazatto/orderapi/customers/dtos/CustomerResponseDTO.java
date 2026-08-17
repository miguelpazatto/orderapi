package com.miguelpazatto.orderapi.customers.dtos;

import com.miguelpazatto.orderapi.customers.entities.Customer;

import java.util.UUID;

public record CustomerResponseDTO(
        UUID id,
        String name,
        String email,
        String phone) {

    public CustomerResponseDTO(Customer entity) {
        this (
                entity.getId(),
                entity.getName(),
                entity.getEmail(),
                entity.getPhone()
        );
    }

}
