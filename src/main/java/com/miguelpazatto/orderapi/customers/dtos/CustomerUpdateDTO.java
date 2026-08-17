package com.miguelpazatto.orderapi.customers.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CustomerUpdateDTO(
        @NotBlank(message = "Nome do cliente é obrigatório")
        String name,

        @NotBlank(message = "O telefone do cliente é obrigatório")
        @Pattern(regexp = "^\\(\\d{2}\\) \\d{4,5}-\\d{4}$", message = "Formato inválido. Use (XX) XXXXX-XXXX")
        String phone) {
}
