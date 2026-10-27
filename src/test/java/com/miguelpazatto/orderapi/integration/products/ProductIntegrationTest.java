package com.miguelpazatto.orderapi.integration.products;

import com.miguelpazatto.orderapi.integration.infra.AbstractIntegrationTest;
import com.miguelpazatto.orderapi.products.entities.Product;
import com.miguelpazatto.orderapi.products.repositories.ProductRepository;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class ProductIntegrationTest extends AbstractIntegrationTest {

    private final ProductRepository productRepository;

    @Autowired
    ProductIntegrationTest (ProductRepository productRepository) {
        this.productRepository = productRepository;
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
}
