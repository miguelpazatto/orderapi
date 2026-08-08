package com.miguelpazatto.orderapi.dtos;

import com.miguelpazatto.orderapi.entities.Customer;
import com.miguelpazatto.orderapi.entities.Product;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomerResponseDTO(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        Integer availableStock,
        String sku,
        String productStatus) {

    public CustomerResponseDTO(Customer entity) {
        this(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getPrice(),
                entity.getAvailableStock(),
                entity.getSku(),
                entity.getProductStatus().name()
        );
    }

}
