package com.miguelpazatto.orderapi.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CustomerRequestDTO(
        @NotBlank(message = "Nome do cliente é obrigatório")
        String name,

        @Email(message = "Formato de e-mail inválido")
        @NotBlank(message = "O email do cliente é obrigatório")
        String email,

        @NotBlank(message = "O telefone do cliente é obrigatório")
        @Pattern(regexp = "^\\(\\d{2}\\) \\d{4,5}-\\d{4}$", message = "Formato inválido. Use (XX) XXXXX-XXXX")
        String phone) {
}
