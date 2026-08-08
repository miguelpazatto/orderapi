package com.miguelpazatto.orderapi.dtos;

import jakarta.validation.constraints.NotBlank;

public record ProductUpdateDetailsRequestDTO(
        @NotBlank(message = "O nome do produto não pode estar vazio")
        String name,

        @NotBlank(message = "A descrição do produto não pode estar vazia")
        String description) {
}
