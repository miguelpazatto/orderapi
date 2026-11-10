package com.miguelpazatto.orderapi.unit.auth.services;

import com.miguelpazatto.orderapi.auth.entities.User;
import com.miguelpazatto.orderapi.auth.repositories.UserRepository;
import com.miguelpazatto.orderapi.auth.services.AuthorizationService;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {
    @Mock
    private UserRepository users;
    @InjectMocks
    private AuthorizationService service;

    @Test
    void shouldLoadExistingUser() {
        User user = new User("user@test.com", "encoded", Set.of());
        when(users.findByEmail(user.getEmail())).thenReturn(user);
        assertThat(service.loadUserByUsername(user.getEmail())).isSameAs(user);
    }

    @Test
    void shouldRejectUnknownUser() {
        assertThatThrownBy(() -> service.loadUserByUsername("missing@test.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
