package com.miguelpazatto.orderapi.dtos;

import java.math.BigDecimal;

public record ProductRequestDTO(
        String name,
        String description,
        BigDecimal price,
        Integer availableStock,
        String sku) {
}
