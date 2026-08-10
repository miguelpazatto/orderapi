package com.miguelpazatto.orderapi.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerRegistrationDTO(
        @NotBlank(message = "O nome não pode estar em branco.")
        String name,

        @NotBlank(message = "O telefone não pode estar em branco.")
        String phone,

        @NotBlank(message = "O e-mail não pode estar em branco.")
        @Email(message = "O formato do e-mail é inválido.")
        String email,

        @NotBlank(message = "A senha não pode estar em branco.")
        @Size(min = 8, message = "A senha deve ter no mínimo 8 caracteres")
        String password
) {
}