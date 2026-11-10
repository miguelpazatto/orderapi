package com.miguelpazatto.orderapi.integration.customers;

import com.miguelpazatto.orderapi.auth.entities.User;
import com.miguelpazatto.orderapi.auth.repositories.UserRepository;
import com.miguelpazatto.orderapi.customers.dtos.CustomerUpdateDTO;
import com.miguelpazatto.orderapi.customers.entities.Customer;
import com.miguelpazatto.orderapi.customers.repositories.CustomerRepository;
import com.miguelpazatto.orderapi.integration.infra.AbstractIntegrationTest;
import com.miguelpazatto.orderapi.integration.infra.TestUserFactory;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

class CustomerIntegrationTest extends AbstractIntegrationTest {
    @Autowired
    private CustomerRepository customers;
    @Autowired
    private UserRepository users;
    @Autowired
    private TestUserFactory factory;
    private User owner;
    private User admin;
    private Customer customer;

    @BeforeEach
    void setUp() {
        customers.deleteAll();
        owner = factory.createCustomer();
        admin = factory.createAdmin();
        customer = customers.save(new Customer(owner.getId(), "Cliente", owner.getEmail(), "(11) 99999-9999"));
    }

    @Test
    void shouldListOnlyActiveProfilesForAdmin() {
        User other = factory.createCustomer();
        Customer inactive = new Customer(other.getId(), "Inativo", other.getEmail(), "phone");
        inactive.deactivate(); customers.save(inactive);
        given().spec(authenticatedSpec(admin))
        .when().get("/customers")
        .then().log().ifValidationFails().statusCode(200).body("size()", equalTo(1))
                .body("[0].id", equalTo(customer.getId().toString()));
    }

    @Test
    void shouldReturnEmptyListWhenNoActiveProfilesExist() {
        customer.deactivate(); customers.save(customer);
        given().spec(authenticatedSpec(admin)).get("/customers")
                .then().statusCode(200).body("size()", equalTo(0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldReadAndUpdateProfileAsOwnerOrAdmin(boolean isAdmin) {
        User requester = isAdmin ? admin : owner;
        given().spec(authenticatedSpec(requester))
        .when().get("/customers/{id}", customer.getId())
        .then().log().ifValidationFails().statusCode(200).body("email", equalTo(owner.getEmail()));
        given().spec(authenticatedSpec(requester)).body(new CustomerUpdateDTO("Novo", "(11) 98888-7777"))
        .when().patch("/customers/{id}", customer.getId())
        .then().log().ifValidationFails().statusCode(200).body("name", equalTo("Novo"));
        assertThat(customers.findById(customer.getId()).orElseThrow().getPhone()).isEqualTo("(11) 98888-7777");
    }

    @ParameterizedTest
    @MethodSource("invalidUpdates")
    void shouldRejectInvalidUpdate(CustomerUpdateDTO request) {
        given().spec(authenticatedSpec(owner)).body(request)
        .when().patch("/customers/{id}", customer.getId())
        .then().log().ifValidationFails().statusCode(400);
        assertThat(customers.findById(customer.getId()).orElseThrow().getName()).isEqualTo("Cliente");
    }

    @Test
    void shouldSynchronizeUserStatusOnDeactivateAndActivate() {
        given().spec(authenticatedSpec(admin))
        .when().delete("/customers/{id}", customer.getId())
        .then().log().ifValidationFails().statusCode(204);
        assertThat(users.findById(owner.getId()).orElseThrow().isEnabled()).isFalse();
        assertThat(customers.findById(customer.getId()).orElseThrow().isActive()).isFalse();
        given().spec(authenticatedSpec(admin))
        .when().patch("/customers/{id}/activate", customer.getId())
        .then().log().ifValidationFails().statusCode(204);
        assertThat(users.findById(owner.getId()).orElseThrow().isEnabled()).isTrue();
        assertThat(customers.findById(customer.getId()).orElseThrow().isActive()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PATCH"})
    void shouldForbidAccessToAnotherProfile(String method) {
        User other = factory.createCustomer();
        Customer profile = customers.save(new Customer(other.getId(), "Outro", other.getEmail(), "phone"));
        given().spec(authenticatedSpec(owner)).body(new CustomerUpdateDTO("Novo", "(11) 99999-9999"))
        .when().request(method, "/customers/" + profile.getId())
        .then().log().ifValidationFails().statusCode(403);
    }

    @ParameterizedTest
    @MethodSource("adminEndpoints")
    void shouldForbidAdministrativeOperationsForCustomer(String method, String endpoint) {
        given().spec(authenticatedSpec(owner))
        .when().request(method, endpoint.replace("{id}", customer.getId().toString()))
        .then().log().ifValidationFails().statusCode(403);
    }

    @ParameterizedTest
    @MethodSource("allEndpoints")
    void shouldForbidAnonymousRequests(String method, String endpoint) {
        given().spec(requestSpecification).body(new CustomerUpdateDTO("Novo", "(11) 99999-9999"))
        .when().request(method, endpoint.replace("{id}", customer.getId().toString()))
        .then().log().ifValidationFails().statusCode(403);
    }

    @ParameterizedTest
    @MethodSource("profileEndpoints")
    void shouldRejectMissingAndMalformedIds(String method, String endpoint) {
        for (String id : List.of(UUID.randomUUID().toString(), "invalid")) {
            given().spec(authenticatedSpec(admin)).body(new CustomerUpdateDTO("Novo", "(11) 99999-9999"))
            .when().request(method, endpoint.replace("{id}", id))
            .then().log().ifValidationFails().statusCode(id.equals("invalid") ? 400 : 404);
        }
    }

    static Stream<CustomerUpdateDTO> invalidUpdates() {
        return Stream.concat(Stream.of(null, "", "   ").map(value -> new CustomerUpdateDTO(value, "(11) 99999-9999")),
                Stream.of(null, "", "   ", "invalid", "11999999999").map(value -> new CustomerUpdateDTO("Novo", value)));
    }
    static Stream<Arguments> adminEndpoints() {
        return Stream.of(Arguments.of("GET", "/customers"), Arguments.of("DELETE", "/customers/{id}"),
                Arguments.of("PATCH", "/customers/{id}/activate"));
    }
    static Stream<Arguments> profileEndpoints() {
        return Stream.of(Arguments.of("GET", "/customers/{id}"), Arguments.of("PATCH", "/customers/{id}"),
                Arguments.of("DELETE", "/customers/{id}"), Arguments.of("PATCH", "/customers/{id}/activate"));
    }
    static Stream<Arguments> allEndpoints() {
        return Stream.concat(Stream.of(Arguments.of("GET", "/customers")), profileEndpoints());
    }
}
