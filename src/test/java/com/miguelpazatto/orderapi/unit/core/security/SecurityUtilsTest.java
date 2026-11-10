package com.miguelpazatto.orderapi.unit.core.security;

import com.miguelpazatto.orderapi.auth.entities.User;
import com.miguelpazatto.orderapi.core.utils.SecurityUtils;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;

class SecurityUtilsTest {
    @AfterEach
    void cleanUp() { SecurityContextHolder.clearContext(); }

    @Test
    void shouldReturnAuthenticatedUser() {
        User user = new User("user@test.com", "encoded", Set.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        assertThat(SecurityUtils.getAuthenticatedUser()).isSameAs(user);
    }

    @Test
    void shouldRejectMissingUnauthenticatedOrUnexpectedPrincipal() {
        assertThatThrownBy(SecurityUtils::getAuthenticatedUser).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user", "password"));
        assertThatThrownBy(SecurityUtils::getAuthenticatedUser).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user", null, List.of()));
        assertThatThrownBy(SecurityUtils::getAuthenticatedUser).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
