package com.miguelpazatto.orderapi.core.utils;

import com.miguelpazatto.orderapi.auth.entities.User;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.file.AccessDeniedException;

public class SecurityUtils {

    public static User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Usuário não está autenticado no contexto de segurança.");
        }

        if (!(authentication.getPrincipal() instanceof User)) {
            throw new AuthenticationCredentialsNotFoundException("O token fornecido não corresponde a um usuário válido do sistema.");
        }

        return (User) authentication.getPrincipal();
    }

}
