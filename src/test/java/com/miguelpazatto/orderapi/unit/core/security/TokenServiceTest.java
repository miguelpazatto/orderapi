package com.miguelpazatto.orderapi.unit.core.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTCreationException;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.mockStatic;
import com.auth0.jwt.algorithms.Algorithm;
import com.miguelpazatto.orderapi.auth.entities.User;
import com.miguelpazatto.orderapi.core.infra.security.TokenService;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class TokenServiceTest {
    private final TokenService service = new TokenService();

    @BeforeEach
    void setUp() { ReflectionTestUtils.setField(service, "secret", "test-secret"); }

    @Test
    void shouldGenerateTokenWithTwoHourExpiration() {
        Instant before = Instant.now();
        String token = service.generateToken(new User("user@test.com", "encoded", Set.of()));
        assertThat(service.validateToken(token)).isEqualTo("user@test.com");
        assertThat(JWT.decode(token).getExpiresAtAsInstant()).isBetween(before.plusSeconds(7199), Instant.now().plusSeconds(7200));
    }

    @Test
    void shouldWrapTokenCreationFailure() {
        try (MockedStatic<JWT> jwt = mockStatic(JWT.class)) {
            jwt.when(JWT::create).thenThrow(new JWTCreationException("invalid", new IllegalArgumentException()));
            assertThatThrownBy(() -> service.generateToken(new User("user@test.com", "encoded", Set.of())))
                    .isInstanceOf(RuntimeException.class).hasCauseInstanceOf(JWTCreationException.class);
        }
    }

    @Test
    void shouldRejectExpiredTamperedAndForeignTokens() {
        String expired = JWT.create().withIssuer("order-api").withSubject("user@test.com")
                .withExpiresAt(Instant.now().minusSeconds(60)).sign(Algorithm.HMAC256("test-secret"));
        String foreign = JWT.create().withIssuer("other-app").sign(Algorithm.HMAC256("test-secret"));
        assertThat(service.validateToken(expired)).isEmpty();
        assertThat(service.validateToken(foreign)).isEmpty();
        assertThat(service.validateToken("invalid.jwt.token")).isEmpty();
    }
}
