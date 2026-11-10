package com.miguelpazatto.orderapi.unit.auth.services;

import com.miguelpazatto.orderapi.auth.dtos.AuthenticationDTO;
import com.miguelpazatto.orderapi.auth.dtos.CustomerRegistrationDTO;
import com.miguelpazatto.orderapi.auth.entities.User;
import com.miguelpazatto.orderapi.auth.entities.enums.UserRole;
import com.miguelpazatto.orderapi.auth.repositories.UserRepository;
import com.miguelpazatto.orderapi.auth.services.AuthenticationService;
import com.miguelpazatto.orderapi.core.events.CustomerStatusChangedEvent;
import com.miguelpazatto.orderapi.core.events.UserRegisteredEvent;
import com.miguelpazatto.orderapi.core.exceptions.DataConflictException;
import com.miguelpazatto.orderapi.core.exceptions.ResourceNotFoundException;
import com.miguelpazatto.orderapi.core.infra.security.TokenService;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {
    @Mock
    private UserRepository users;
    @Mock
    private PasswordEncoder encoder;
    @Mock
    private TokenService tokens;
    @Mock
    private AuthenticationManager manager;
    @Mock
    private ApplicationEventPublisher events;
    @InjectMocks
    private AuthenticationService service;

    @Test
    void shouldEncodePasswordRegisterCustomerAndPublishEvent() {
        CustomerRegistrationDTO request = new CustomerRegistrationDTO("Cliente", "(11) 99999-9999", "user@test.com", "password1");
        when(encoder.encode(request.password())).thenReturn("encoded");
        when(users.save(any(User.class))).thenAnswer(call -> {
            User user = call.getArgument(0);
            ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
            return user;
        });
        when(tokens.generateToken(any())).thenReturn("jwt");
        assertThat(service.register(request).token()).isEqualTo("jwt");
        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(users).save(user.capture());
        assertThat(user.getValue().getPassword()).isEqualTo("encoded");
        assertThat(user.getValue().getRoles()).containsExactly(UserRole.CUSTOMER);
        verify(events).publishEvent(new UserRegisteredEvent(user.getValue().getId(), request.name(), request.email(), request.phone()));
    }

    @Test
    void shouldRejectDuplicateEmailWithoutSaving() {
        when(users.findByEmail("user@test.com")).thenReturn(new User("user@test.com", "encoded", Set.of(UserRole.CUSTOMER)));
        assertThatThrownBy(() -> service.register(new CustomerRegistrationDTO("Cliente", "phone", "user@test.com", "password1")))
                .isInstanceOf(DataConflictException.class);
        verify(users, never()).save(any());
        verifyNoInteractions(encoder, tokens, events);
    }

    @Test
    void shouldAuthenticateAndGenerateToken() {
        User user = new User("user@test.com", "encoded", Set.of(UserRole.CUSTOMER));
        when(manager.authenticate(any())).thenReturn(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        when(tokens.generateToken(user)).thenReturn("jwt");
        assertThat(service.login(new AuthenticationDTO(user.getEmail(), "password1")).token()).isEqualTo("jwt");
    }

    @Test
    void shouldNotGenerateTokenWhenAuthenticationFails() {
        when(manager.authenticate(any())).thenThrow(new BadCredentialsException("Invalid"));
        assertThatThrownBy(() -> service.login(new AuthenticationDTO("user@test.com", "password1")))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(tokens);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldSynchronizeCustomerStatus(boolean active) {
        UUID id = UUID.randomUUID();
        User user = new User("user@test.com", "encoded", Set.of(UserRole.CUSTOMER));
        when(users.findById(id)).thenReturn(Optional.of(user));
        service.onCustomerStatusChanged(new CustomerStatusChangedEvent(id, active));
        assertThat(user.isEnabled()).isEqualTo(active);
        verify(users).save(user);
    }

    @Test
    void shouldRejectStatusEventForMissingUser() {
        UUID id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.onCustomerStatusChanged(new CustomerStatusChangedEvent(id, false)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(users, never()).save(any());
    }
}
