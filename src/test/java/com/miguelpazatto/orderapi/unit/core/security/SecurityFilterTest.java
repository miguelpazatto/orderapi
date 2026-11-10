package com.miguelpazatto.orderapi.unit.core.security;

import com.miguelpazatto.orderapi.auth.entities.User;
import com.miguelpazatto.orderapi.auth.entities.enums.UserRole;
import com.miguelpazatto.orderapi.auth.repositories.UserRepository;
import com.miguelpazatto.orderapi.core.infra.security.SecurityFilter;
import com.miguelpazatto.orderapi.core.infra.security.TokenService;
import jakarta.servlet.FilterChain;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityFilterTest {
    @Mock
    private TokenService tokens;
    @Mock
    private UserRepository users;
    @Mock
    private FilterChain chain;
    @InjectMocks
    private SecurityFilter filter;

    @AfterEach
    void cleanUp() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"Basic abc", "JWT abc"})
    void shouldIgnoreMissingOrUnsupportedHeader(String header) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (header != null) request.addHeader("Authorization", header);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(tokens, users);
        verify(chain).doFilter(request, response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"active", "inactive", "missing", "invalid"})
    void shouldAuthenticateOnlyExistingActiveUserWithValidToken(String scenario) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer jwt");
        when(tokens.validateToken("jwt")).thenReturn(scenario.equals("invalid") ? "" : "user@test.com");
        User user = new User("user@test.com", "encoded", Set.of(UserRole.CUSTOMER));
        if (scenario.equals("inactive")) user.deactivate();
        if (!scenario.equals("invalid")) when(users.findByEmail(user.getEmail())).thenReturn(scenario.equals("missing") ? null : user);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        if (scenario.equals("active")) {
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isSameAs(user);
        } else {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }
}
