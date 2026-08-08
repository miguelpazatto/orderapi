package com.miguelpazatto.orderapi.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ProductRequestDTO(
        @NotBlank(message = "Nome do produto é obrigatório")
        String name,

        @NotBlank(message = "Descrição do produto é obrigatória")
        String description,

        @NotNull(message = "Preço do produto não pode ser nulo")
        BigDecimal price,

        @PositiveOrZero(message = "O valor mínimo do estoque é 0")
        Integer availableStock,

        @NotBlank(message = "SKU válido do produto é obrigatório")
        String sku) {
}
