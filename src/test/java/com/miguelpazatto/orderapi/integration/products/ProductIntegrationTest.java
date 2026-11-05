package com.miguelpazatto.orderapi.integration.products;

import com.miguelpazatto.orderapi.auth.entities.User;
import com.miguelpazatto.orderapi.auth.entities.enums.UserRole;
import com.miguelpazatto.orderapi.core.infra.security.TokenService;
import com.miguelpazatto.orderapi.integration.infra.AbstractIntegrationTest;
import com.miguelpazatto.orderapi.integration.infra.TestUserFactory;
import com.miguelpazatto.orderapi.products.dtos.ProductRequestDTO;
import com.miguelpazatto.orderapi.products.entities.Product;
import com.miguelpazatto.orderapi.products.repositories.ProductRepository;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerTypePredicate;

import javax.management.relation.Role;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class ProductIntegrationTest extends AbstractIntegrationTest {

    private final ProductRepository productRepository;
    private final TokenService tokenService;
    private final TestUserFactory testUserFactory;

    @Autowired
    ProductIntegrationTest(
            ProductRepository productRepository,
            TokenService tokenService,
            TestUserFactory testUserFactory
    ) {
        this.productRepository = productRepository;
        this.tokenService = tokenService;
        this.testUserFactory = testUserFactory;
    }

    @BeforeEach
    void cleanDatabase() {
        productRepository.deleteAll();
    }

    @Test
    void shouldReturnAllProducts() {

        Product firstProduct = new Product(
                "Teclado",
                "Teclado mecânico",
                new BigDecimal("150.00"),
                10,
                "TEST-KEYBOARD"
        );

        Product secondProduct = new Product(
                "Monitor",
                "Monitor FHD",
                new BigDecimal("500.00"),
                15,
                "TEST-MONITOR"
        );

        productRepository.saveAll(List.of(firstProduct, secondProduct));

        given()
                .spec(requestSpecification)
                .when()
                .get("/products")
                .then()
                .log().ifValidationFails()
                .statusCode(HttpStatus.OK.value())
                .contentType(ContentType.JSON)
                .body("size()", equalTo(2))
                .body("name", containsInAnyOrder(
                        firstProduct.getName(),
                        secondProduct.getName()))
                .body(
                        "find {it.id == '" + firstProduct.getId() + "'}.name",
                        equalTo(firstProduct.getName())
                )
                .body(
                        "find {it.id == '" + secondProduct.getId() + "'}.name",
                        equalTo(secondProduct.getName())
                );
    }

    @Test
    void shouldReturnEmptyListWhenThereAreNoProducts() {

        given()
                .spec(requestSpecification)
                .when()
                .get("/products")
                .then()
                .log().ifValidationFails()
                .statusCode(HttpStatus.OK.value())
                .contentType(ContentType.JSON)
                .body("size()", equalTo(0));

    }

    @Test
    void shouldReturnProductWhenIdExists() {

        Product product = new Product(
                "Teclado",
                "Teclado mecânico",
                new BigDecimal("150.00"),
                10,
                "TEST-KEYBOARD"
        );

        productRepository.save(product);

        given()
                .spec(requestSpecification)
                .when()
                .get("/products/{id}", product.getId())
                .then()
                .log().ifValidationFails()
                .statusCode(HttpStatus.OK.value())
                .contentType(ContentType.JSON)
                .body("id", equalTo(product.getId().toString()))
                .body("name", equalTo(product.getName()));

    }

    @Test
    void shouldReturnNotFoundWhenIdDoesNotExist() {
        UUID nonexistentId = UUID.randomUUID();

        given()
                .spec(requestSpecification)
                .when()
                .get("products/{id}", nonexistentId)
                .then()
                .log().ifValidationFails()
                .statusCode(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void shouldCreateProductWhenDataIsValid() {

        User admin = testUserFactory.createAdmin();

        ProductRequestDTO newProduct = new ProductRequestDTO(
                "Teclado",
                "Teclado mecânico",
                new BigDecimal("150.00"),
                10,
                "TEST-KEYBOARD"
        );

        given()
                .spec(authenticatedSpec(admin))
                .body(newProduct)
                .when()
                .post("/products")
                .then()
                .log().ifValidationFails()
                .statusCode(HttpStatus.CREATED.value())
                .contentType(ContentType.JSON)
                .body("name", equalTo(newProduct.name()))
                .body("sku", equalTo(newProduct.sku()));
    }

    @ParameterizedTest(name = "{index} - {0}")
    @MethodSource("invalidProducts")
    void shouldNotCreateProductWhenDataIsInvalid(
        String scenario,
        ProductRequestDTO request
    ){
        User admin = testUserFactory.createAdmin();

        given()
                .spec(authenticatedSpec(admin))
                .body(request)
        .when()
                .post("/products")
        .then()
                .log().ifValidationFails()
                .statusCode(HttpStatus.BAD_REQUEST.value());

        assertThat(productRepository.count()).isZero();

    }

    static Stream<Arguments> invalidProducts() {
        return Stream.of(
                Arguments.of(
                        "Nome vazio",
                        new ProductRequestDTO(
                                "",
                                "Teclado mecânico",
                                new BigDecimal("150.00"),
                                10,
                                "TEST_KEYBOARD"
                        )
                ),

                Arguments.of(
                        "Preço negativo",
                        new ProductRequestDTO(
                                "Teclado",
                                "Teclado mecânico",
                                new BigDecimal("-150.00"),
                                10,
                                "TEST_KEYBOARD"
                        )
                ),

                Arguments.of(
                        "Estoque negativo",
                        new ProductRequestDTO(
                                "Teclado",
                                "Teclado mecânico",
                                new BigDecimal("150.00"),
                                -10,
                                "TEST_KEYBOARD"
                        )
                )
        );
    }
}
