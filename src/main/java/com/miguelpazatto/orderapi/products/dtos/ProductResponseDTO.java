package com.miguelpazatto.orderapi.products.dtos;

import com.miguelpazatto.orderapi.products.entities.Product;
import com.miguelpazatto.orderapi.products.entities.enums.ProductStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponseDTO(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        Integer availableStock,
        ProductStatus productStatus,
        String sku
) {

    public ProductResponseDTO(Product entity) {
        this(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getPrice(),
                entity.getAvailableStock(),
                entity.getProductStatus(),
                entity.getSku()
        );
    }
}