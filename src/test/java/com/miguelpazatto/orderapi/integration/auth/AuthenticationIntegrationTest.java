package com.miguelpazatto.orderapi.integration.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.miguelpazatto.orderapi.auth.dtos.AuthenticationDTO;
import com.miguelpazatto.orderapi.auth.dtos.CustomerRegistrationDTO;
import com.miguelpazatto.orderapi.auth.entities.User;
import com.miguelpazatto.orderapi.auth.entities.enums.UserRole;
import com.miguelpazatto.orderapi.auth.repositories.UserRepository;
import com.miguelpazatto.orderapi.customers.repositories.CustomerRepository;
import com.miguelpazatto.orderapi.integration.infra.AbstractIntegrationTest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

class AuthenticationIntegrationTest extends AbstractIntegrationTest {
    @Autowired
    private UserRepository users;
    @Autowired
    private CustomerRepository customers;
    @Autowired
    private PasswordEncoder encoder;

    @BeforeEach
    void cleanProfiles() { customers.deleteAll(); }

    @Test
    void shouldRegisterEncodedUserAndCustomerWithUsableToken() {
        String email = "register-" + UUID.randomUUID() + "@test.com";
        String token = given().spec(requestSpecification)
                .body(new CustomerRegistrationDTO("Cliente", "(11) 99999-9999", email, "password1"))
        .when().post("/auth/register")
        .then().log().ifValidationFails().statusCode(201).body("token", not(emptyOrNullString()))
                .extract().path("token");

        User user = (User) users.findByEmail(email);
        assertThat(encoder.matches("password1", user.getPassword())).isTrue();
        assertThat(user.getRoles()).containsExactly(UserRole.CUSTOMER);
        var profile = customers.findByUserId(user.getId()).orElseThrow();
        assertThat(profile.getEmail()).isEqualTo(email);
        assertThat(profile.getName()).isEqualTo("Cliente");
        given().spec(requestSpecification).header("Authorization", "Bearer " + token)
        .when().get("/customers/{id}", profile.getId())
        .then().statusCode(200);
    }

    @Test
    void shouldRejectDuplicateRegistrationWithoutAnotherProfile() {
        String email = "duplicate-" + UUID.randomUUID() + "@test.com";
        CustomerRegistrationDTO request = new CustomerRegistrationDTO("Cliente", "phone", email, "password1");
        given().spec(requestSpecification).body(request).post("/auth/register").then().statusCode(201);
        long before = users.count();
        given().spec(requestSpecification).body(request)
        .when().post("/auth/register")
        .then().log().ifValidationFails().statusCode(409);
        assertThat(users.count()).isEqualTo(before);
        assertThat(customers.count()).isEqualTo(1);
    }

    @ParameterizedTest(name = "{index} - {0}: {1}")
    @MethodSource("invalidRegistration")
    void shouldRejectInvalidRegistration(String field, String value) {
        Map<String, Object> body = new LinkedHashMap<>(Map.of("name", "Cliente", "phone", "phone",
                "email", "invalid-" + UUID.randomUUID() + "@test.com", "password", "password1"));
        body.put(field, value);
        long before = users.count();
        given().spec(requestSpecification).body(body)
        .when().post("/auth/register")
        .then().log().ifValidationFails().statusCode(400);
        assertThat(users.count()).isEqualTo(before);
        assertThat(customers.count()).isZero();
    }

    @Test
    void shouldLoginWithEncodedPassword() {
        User user = saveUser();
        String token = given().spec(requestSpecification).body(new AuthenticationDTO(user.getEmail(), "password1"))
        .when().post("/auth/login")
        .then().log().ifValidationFails().statusCode(200).extract().path("token");
        assertThat(tokenService.validateToken(token)).isEqualTo(user.getEmail());
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-password", "unknown-user", "inactive-user"})
    void shouldRejectInvalidCredentials(String scenario) {
        User user = saveUser();
        if (scenario.equals("inactive-user")) { user.deactivate(); users.save(user); }
        given().spec(requestSpecification).body(new AuthenticationDTO(
                scenario.equals("unknown-user") ? UUID.randomUUID() + "@test.com" : user.getEmail(),
                scenario.equals("wrong-password") ? "wrong-password" : "password1"))
        .when().post("/auth/login")
        .then().log().ifValidationFails().statusCode(401);
    }

    @ParameterizedTest(name = "{index} - {0}: {1}")
    @MethodSource("invalidLogin")
    void shouldRejectInvalidLoginFields(String field, String value) {
        Map<String, Object> body = new LinkedHashMap<>(Map.of("email", "user@test.com", "password", "password1"));
        body.put(field, value);
        given().spec(requestSpecification).body(body)
        .when().post("/auth/login")
        .then().log().ifValidationFails().statusCode(400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired", "invalid", "inactive", "missing", "wrong-prefix"})
    void shouldRejectInvalidOrRevokedBearerToken(String scenario) {
        User user = saveUser();
        String token = tokenService.generateToken(user);
        if (scenario.equals("expired")) token = JWT.create().withIssuer("order-api").withSubject(user.getEmail())
                .withExpiresAt(Instant.now().minusSeconds(60)).sign(Algorithm.HMAC256("integration-test-secret-key"));
        if (scenario.equals("invalid")) token = "invalid.jwt.token";
        if (scenario.equals("inactive")) { user.deactivate(); users.save(user); }
        if (scenario.equals("missing")) users.delete(user);
        given().spec(requestSpecification).header("Authorization", (scenario.equals("wrong-prefix") ? "Basic " : "Bearer ") + token)
        .when().get("/orders")
        .then().log().ifValidationFails().statusCode(403);
    }

    static Stream<Arguments> invalidRegistration() {
        return Stream.concat(Stream.of("name", "phone", "email", "password").flatMap(field ->
                Stream.of(null, "", "   ").map(value -> Arguments.of(field, value))),
                Stream.of(Arguments.of("email", "invalid"), Arguments.of("password", "short")));
    }

    static Stream<Arguments> invalidLogin() {
        return Stream.concat(Stream.of("email", "password").flatMap(field ->
                Stream.of(null, "", "   ").map(value -> Arguments.of(field, value))),
                Stream.of(Arguments.of("email", "invalid"), Arguments.of("password", "short")));
    }

    private User saveUser() {
        return users.save(new User("login-" + UUID.randomUUID() + "@test.com", encoder.encode("password1"), Set.of(UserRole.CUSTOMER)));
    }
}
