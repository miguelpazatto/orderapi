package com.miguelpazatto.orderapi.auth.services;

import com.miguelpazatto.orderapi.auth.dtos.AuthenticationDTO;
import com.miguelpazatto.orderapi.auth.dtos.CustomerRegistrationDTO;
import com.miguelpazatto.orderapi.auth.dtos.TokenDTO;
import com.miguelpazatto.orderapi.auth.entities.User;
import com.miguelpazatto.orderapi.auth.entities.enums.UserRole;
import com.miguelpazatto.orderapi.auth.repositories.UserRepository;
import com.miguelpazatto.orderapi.core.events.CustomerStatusChangedEvent;
import com.miguelpazatto.orderapi.core.events.UserRegisteredEvent;
import com.miguelpazatto.orderapi.core.exceptions.DataConflictException;
import com.miguelpazatto.orderapi.core.exceptions.ResourceNotFoundException;
import com.miguelpazatto.orderapi.core.infra.security.TokenService;
import com.miguelpazatto.orderapi.customers.dtos.CustomerResponseDTO;
import com.miguelpazatto.orderapi.customers.services.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuthenticationManager authenticationManager;
    private final ApplicationEventPublisher eventPublisher;

    public TokenDTO login(AuthenticationDTO dto) {
        var usernamePassword = new UsernamePasswordAuthenticationToken(dto.email(), dto.password());
        var auth = authenticationManager.authenticate(usernamePassword);
        var token = tokenService.generateToken((User) auth.getPrincipal());
        return new TokenDTO(token);
    }

    @Transactional
    public TokenDTO register(CustomerRegistrationDTO dto) {
        if (userRepository.findByEmail(dto.email()) != null) {
            throw new DataConflictException("Este e-mail já está cadastrado no sistema.");
        }

        String encryptedPassword = passwordEncoder.encode(dto.password());

        User newUser = new User(
                dto.email(),
                encryptedPassword,
                Set.of(UserRole.CUSTOMER)
        );

        User savedUser = userRepository.save(newUser);

        eventPublisher.publishEvent(new UserRegisteredEvent(
                savedUser.getId(), dto.name(), dto.email(), dto.phone()
        ));

        String token = tokenService.generateToken(savedUser);
        return new TokenDTO(token);
    }

    @EventListener
    public void onCustomerStatusChanged(CustomerStatusChangedEvent event) {
        User user = userRepository.findById(event.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário com ID " + event.userId() + " não encontrado"));

        if (event.isActive()) {
            user.activate();
        } else {
            user.deactivate();
        }

        userRepository.save(user);
    }
}
