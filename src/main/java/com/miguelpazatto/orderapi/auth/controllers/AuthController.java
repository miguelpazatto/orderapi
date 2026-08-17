package com.miguelpazatto.orderapi.auth.controllers;

import com.miguelpazatto.orderapi.auth.dtos.AuthenticationDTO;
import com.miguelpazatto.orderapi.auth.dtos.CustomerRegistrationDTO;
import com.miguelpazatto.orderapi.auth.dtos.TokenDTO;
import com.miguelpazatto.orderapi.auth.entities.User;
import com.miguelpazatto.orderapi.auth.entities.enums.UserRole;
import com.miguelpazatto.orderapi.auth.repositories.UserRepository;
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
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final CustomerService customerService;

    @PostMapping("/login")
    public ResponseEntity<TokenDTO> login(@RequestBody @Valid AuthenticationDTO dto) {
        var usernamePassword = new UsernamePasswordAuthenticationToken(dto.email(), dto.password());
        var auth = this.authenticationManager.authenticate(usernamePassword);
        var token = tokenService.generateToken((User) auth.getPrincipal());
        return ResponseEntity.ok(new TokenDTO(token));
    }

    @PostMapping("/register")
    public ResponseEntity<CustomerResponseDTO> register(@RequestBody @Valid CustomerRegistrationDTO dto) {
        if (userRepository.findByEmail(dto.email()) != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        String encryptedPassword = passwordEncoder.encode(dto.password());
        User newUser = new User();
        newUser.setEmail(dto.email());
        newUser.setPassword(encryptedPassword);

        newUser.setRoles(Set.of(UserRole.CUSTOMER));

        User savedUser = userRepository.save(newUser);
        var createdCustomer = customerService.createCustomerProfile(dto, savedUser);

        var uri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/customers/{id}")
                .buildAndExpand(createdCustomer.id())
                .toUri();

        CustomerResponseDTO response = new CustomerResponseDTO(
                createdCustomer.id(),
                createdCustomer.name(),
                createdCustomer.email(),
                createdCustomer.phone()
        );

        return ResponseEntity.created(uri).body(response);
    }
}