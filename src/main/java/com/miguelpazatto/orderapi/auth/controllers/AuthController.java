package com.miguelpazatto.orderapi.auth.controllers;

import com.miguelpazatto.orderapi.auth.dtos.AuthenticationDTO;
import com.miguelpazatto.orderapi.auth.dtos.CustomerRegistrationDTO;
import com.miguelpazatto.orderapi.auth.dtos.TokenDTO;
import com.miguelpazatto.orderapi.auth.entities.User;
import com.miguelpazatto.orderapi.auth.entities.enums.UserRole;
import com.miguelpazatto.orderapi.auth.repositories.UserRepository;
import com.miguelpazatto.orderapi.auth.services.AuthenticationService;
import com.miguelpazatto.orderapi.core.infra.security.TokenService;
import com.miguelpazatto.orderapi.customers.dtos.CustomerResponseDTO;
import com.miguelpazatto.orderapi.customers.services.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Set;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final AuthenticationService authService;

    @PostMapping("/login")
    public ResponseEntity<TokenDTO> login(@RequestBody @Valid AuthenticationDTO dto) {
        TokenDTO tokenDto = authService.login(dto);
        return ResponseEntity.ok(tokenDto);
    }

    @PostMapping("/register")
    public ResponseEntity<TokenDTO> register(@RequestBody @Valid CustomerRegistrationDTO dto) {
        TokenDTO tokenDto = authService.register(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(tokenDto);
    }
}