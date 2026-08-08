package com.miguelpazatto.orderapi.dtos;

import com.miguelpazatto.orderapi.entities.Product;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponseDTO(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        Integer availableStock,
        String sku,
        String productStatus) {

    public ProductResponseDTO(Product entity) {
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
