package com.miguelpazatto.orderapi.integration.products;

import com.miguelpazatto.orderapi.integration.infra.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;

public class ProductIntegrationTest extends AbstractIntegrationTest {

    @Test
    void shouldReturnAllProducts() {
        given()
            .spec(requestSpecification)
        .when()
            .get("/products")
        .then()
            .log().ifValidationFails()
            .statusCode(HttpStatus.OK.value());
    }

}
