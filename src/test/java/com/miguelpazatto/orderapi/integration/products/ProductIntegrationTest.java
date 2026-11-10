package com.miguelpazatto.orderapi.integration.products;

import com.miguelpazatto.orderapi.auth.entities.User;
import com.miguelpazatto.orderapi.integration.infra.AbstractIntegrationTest;
import com.miguelpazatto.orderapi.integration.infra.TestUserFactory;
import com.miguelpazatto.orderapi.products.dtos.ProductRequestDTO;
import com.miguelpazatto.orderapi.products.dtos.ProductUpdateDetailsRequestDTO;
import com.miguelpazatto.orderapi.products.entities.Product;
import com.miguelpazatto.orderapi.products.entities.enums.ProductStatus;
import com.miguelpazatto.orderapi.products.repositories.ProductRepository;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;

public class ProductIntegrationTest extends AbstractIntegrationTest {

    private final ProductRepository productRepository;
    private final TestUserFactory testUserFactory;

    @Autowired
    ProductIntegrationTest(
            ProductRepository productRepository,
            TestUserFactory testUserFactory
    ) {
        this.productRepository = productRepository;
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
                .get("/products/{id}", nonexistentId)
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
    ) {
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

    @Test
    void shouldReturnConflictWhenSkuIsDuplicated() {
        Product product = new Product(
                "Teclado",
                "Teclado mecânico",
                new BigDecimal("150.00"),
                10,
                "TEST_KEYBOARD"
        );

        productRepository.save(product);

        User admin = testUserFactory.createAdmin();

        ProductRequestDTO duplicateProduct = new ProductRequestDTO(
                "Outro teclado",
                "Outra descrição",
                new BigDecimal("200.00"),
                5,
                product.getSku()
        );

        given()
                .spec(authenticatedSpec(admin))
                .body(duplicateProduct)
        .when()
                .post("/products")
        .then()
                .log().ifValidationFails()
                .statusCode(HttpStatus.CONFLICT.value())
                .body("title", equalTo("Data Conflict"))
                .body("detail", equalTo("Já existe um produto cadastrado com o SKU: " + product.getSku()));

        assertThat(productRepository.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"20, ACTIVE", "0, OUT_OF_STOCK"})
    void shouldUpdateStockWhenDataIsValid(int newStock, ProductStatus expectedStatus) {
        Product product = saveProduct(newStock == 0 ? 10 : 0);
        User admin = testUserFactory.createAdmin();

        given()
                .spec(authenticatedSpec(admin))
                .queryParam("newStock", newStock)
        .when()
                .patch("/products/{id}/stock", product.getId())
        .then()
                .log().ifValidationFails()
                .statusCode(HttpStatus.OK.value())
                .body("availableStock", equalTo(newStock))
                .body("productStatus", equalTo(expectedStatus.name()));

        assertThat(productRepository.findById(product.getId()).orElseThrow().getAvailableStock())
                .isEqualTo(newStock);
    }

    @Test
    void shouldNotUpdateStockWhenStockIsNegative() {
        Product product = saveProduct(10);
        User admin = testUserFactory.createAdmin();

        given()
                .spec(authenticatedSpec(admin))
                .queryParam("newStock", -1)
        .when()
                .patch("/products/{id}/stock", product.getId())
        .then()
                .log().ifValidationFails()
                .statusCode(HttpStatus.BAD_REQUEST.value());

        assertThat(productRepository.findById(product.getId()).orElseThrow().getAvailableStock())
                .isEqualTo(10);
    }

    @Test
    void shouldUpdatePriceWhenDataIsValid() {
        Product product = saveProduct(10);
        User admin = testUserFactory.createAdmin();
        BigDecimal newPrice = new BigDecimal("200.00");

        given()
                .spec(authenticatedSpec(admin))
                .queryParam("newPrice", newPrice)
        .when()
                .patch("/products/{id}/price", product.getId())
        .then()
                .log().ifValidationFails()
                .statusCode(HttpStatus.OK.value())
                .body("price", equalTo(200.0f));

        assertThat(productRepository.findById(product.getId()).orElseThrow().getPrice())
                .isEqualByComparingTo(newPrice);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1.00"})
    void shouldNotUpdatePriceWhenPriceIsInvalid(String price) {
        Product product = saveProduct(10);
        User admin = testUserFactory.createAdmin();

        given()
                .spec(authenticatedSpec(admin))
                .queryParam("newPrice", price)
        .when()
                .patch("/products/{id}/price", product.getId())
        .then()
                .log().ifValidationFails()
                .statusCode(HttpStatus.BAD_REQUEST.value());

        assertThat(productRepository.findById(product.getId()).orElseThrow().getPrice())
                .isEqualByComparingTo(product.getPrice());
    }

    @Test
    void shouldUpdateDetailsWhenDataIsValid() {
        Product product = saveProduct(10);
        User admin = testUserFactory.createAdmin();
        ProductUpdateDetailsRequestDTO request =
                new ProductUpdateDetailsRequestDTO("Novo teclado", "Nova descrição");

        given()
                .spec(authenticatedSpec(admin))
                .body(request)
        .when()
                .patch("/products/{id}", product.getId())
        .then()
                .log().ifValidationFails()
                .statusCode(HttpStatus.OK.value())
                .body("name", equalTo(request.name()))
                .body("description", equalTo(request.description()));

        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updatedProduct.getName()).isEqualTo(request.name());
        assertThat(updatedProduct.getDescription()).isEqualTo(request.description());
    }

    @ParameterizedTest(name = "{index} - {0}")
    @MethodSource("invalidDetails")
    void shouldNotUpdateDetailsWhenDataIsInvalid(String scenario, ProductUpdateDetailsRequestDTO request) {
        Product product = saveProduct(10);
        User admin = testUserFactory.createAdmin();

        given()
                .spec(authenticatedSpec(admin))
                .body(request)
        .when()
                .patch("/products/{id}", product.getId())
        .then()
                .log().ifValidationFails()
                .statusCode(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void shouldDeactivateActiveProduct() {
        Product product = saveProduct(10);
        User admin = testUserFactory.createAdmin();

        given()
                .spec(authenticatedSpec(admin))
        .when()
                .patch("/products/{id}/deactivate", product.getId())
        .then()
                .log().ifValidationFails()
                .statusCode(HttpStatus.NO_CONTENT.value());

        assertThat(productRepository.findById(product.getId()).orElseThrow().getProductStatus())
                .isEqualTo(ProductStatus.INACTIVE);
    }

    @Test
    void shouldReturnConflictWhenProductIsAlreadyInactive() {
        Product product = saveProduct(10);
        product.deactivate();
        productRepository.save(product);
        User admin = testUserFactory.createAdmin();

        given()
                .spec(authenticatedSpec(admin))
        .when()
                .patch("/products/{id}/deactivate", product.getId())
        .then()
                .log().ifValidationFails()
                .statusCode(HttpStatus.CONFLICT.value());
    }

    @ParameterizedTest
    @CsvSource({"10, ACTIVE", "0, OUT_OF_STOCK"})
    void shouldActivateInactiveProductAccordingToStock(int stock, ProductStatus expectedStatus) {
        Product product = saveProduct(stock);
        product.deactivate();
        productRepository.save(product);
        User admin = testUserFactory.createAdmin();

        given()
                .spec(authenticatedSpec(admin))
        .when()
                .patch("/products/{id}/activate", product.getId())
        .then()
                .log().ifValidationFails()
                .statusCode(HttpStatus.NO_CONTENT.value());

        assertThat(productRepository.findById(product.getId()).orElseThrow().getProductStatus())
                .isEqualTo(expectedStatus);
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 0})
    void shouldReturnConflictWhenProductAlreadyHasActivationStatus(int stock) {
        Product product = saveProduct(stock);
        User admin = testUserFactory.createAdmin();

        given()
                .spec(authenticatedSpec(admin))
        .when()
                .patch("/products/{id}/activate", product.getId())
        .then()
                .log().ifValidationFails()
                .statusCode(HttpStatus.CONFLICT.value());
    }

    @ParameterizedTest(name = "{index} - PATCH {0}")
    @MethodSource("patchEndpoints")
    void shouldReturnNotFoundWhenUpdatingNonexistentProduct(String endpoint) {
        User admin = testUserFactory.createAdmin();

        given()
                .spec(authenticatedSpec(admin))
                .body(new ProductUpdateDetailsRequestDTO("Teclado", "Descrição"))
        .when()
                .patch(endpoint.replace("{id}", UUID.randomUUID().toString()))
        .then()
                .log().ifValidationFails()
                .statusCode(HttpStatus.NOT_FOUND.value());
    }

    @ParameterizedTest(name = "{index} - {0} {1}")
    @MethodSource("writeEndpoints")
    void shouldForbidProductWritesForCustomer(String method, String endpoint) {
        Product product = saveProduct(10);
        User customer = testUserFactory.createCustomer();

        given()
                .spec(authenticatedSpec(customer))
                .body(new ProductRequestDTO("Teclado", "Descrição", new BigDecimal("200.00"), 5, "OTHER-SKU"))
        .when()
                .request(method, endpoint.replace("{id}", product.getId().toString()))
        .then()
                .log().ifValidationFails()
                .statusCode(HttpStatus.FORBIDDEN.value());
    }

    @ParameterizedTest(name = "{index} - {0} {1}")
    @MethodSource("writeEndpoints")
    void shouldForbidProductWritesWithoutAuthentication(String method, String endpoint) {
        Product product = saveProduct(10);

        given()
                .spec(requestSpecification)
                .body(new ProductRequestDTO("Teclado", "Descrição", new BigDecimal("200.00"), 5, "OTHER-SKU"))
        .when()
                .request(method, endpoint.replace("{id}", product.getId().toString()))
        .then()
                .log().ifValidationFails()
                .statusCode(HttpStatus.FORBIDDEN.value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"stock", "price"})
    void shouldReturnBadRequestWhenUpdateParameterIsMissing(String field) {
        Product product = saveProduct(10);
        User admin = testUserFactory.createAdmin();

        given()
                .spec(authenticatedSpec(admin))
        .when()
                .patch("/products/{id}/{field}", product.getId(), field)
        .then()
                .log().ifValidationFails()
                .statusCode(HttpStatus.BAD_REQUEST.value());
    }

    @ParameterizedTest
    @CsvSource({"stock, newStock", "price, newPrice"})
    void shouldReturnBadRequestWhenUpdateParameterIsNotNumeric(String field, String parameter) {
        Product product = saveProduct(10);
        User admin = testUserFactory.createAdmin();

        given()
                .spec(authenticatedSpec(admin))
                .queryParam(parameter, "invalid")
        .when()
                .patch("/products/{id}/{field}", product.getId(), field)
        .then()
                .log().ifValidationFails()
                .statusCode(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void shouldReturnBadRequestWhenProductIdIsMalformed() {
        given()
                .spec(requestSpecification)
        .when()
                .get("/products/{id}", "invalid-uuid")
        .then()
                .log().ifValidationFails()
                .statusCode(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void shouldNotCreateProductWhenJsonIsMalformed() {
        User admin = testUserFactory.createAdmin();

        given()
                .spec(authenticatedSpec(admin))
                .body("{\"name\":")
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
                        "Nome nulo",
                        new ProductRequestDTO(
                                null,
                                "Teclado mecânico",
                                new BigDecimal("150.00"),
                                10,
                                "TEST_KEYBOARD"
                        )
                ),
                Arguments.of(
                        "Nome em branco",
                        new ProductRequestDTO(
                                "   ",
                                "Teclado mecânico",
                                new BigDecimal("150.00"),
                                10,
                                "TEST_KEYBOARD"
                        )
                ),
                Arguments.of(
                        "Descrição nula",
                        new ProductRequestDTO(
                                "Teclado",
                                null,
                                new BigDecimal("150.00"),
                                10,
                                "TEST_KEYBOARD"
                        )
                ),
                Arguments.of(
                        "Descrição vazia",
                        new ProductRequestDTO(
                                "Teclado",
                                "",
                                new BigDecimal("150.00"),
                                10,
                                "TEST_KEYBOARD"
                        )
                ),
                Arguments.of(
                        "Descrição em branco",
                        new ProductRequestDTO(
                                "Teclado",
                                "   ",
                                new BigDecimal("150.00"),
                                10,
                                "TEST_KEYBOARD"
                        )
                ),
                Arguments.of(
                        "Preço zero",
                        new ProductRequestDTO(
                                "Teclado",
                                "Teclado mecânico",
                                BigDecimal.ZERO,
                                10,
                                "TEST_KEYBOARD"
                        )
                ),
                Arguments.of(
                        "Preço nulo",
                        new ProductRequestDTO(
                                "Teclado",
                                "Teclado mecânico",
                                null,
                                10,
                                "TEST_KEYBOARD"
                        )
                ),
                Arguments.of(
                        "Estoque nulo",
                        new ProductRequestDTO(
                                "Teclado",
                                "Teclado mecânico",
                                new BigDecimal("150.00"),
                                null,
                                "TEST_KEYBOARD"
                        )
                ),
                Arguments.of(
                        "SKU nulo",
                        new ProductRequestDTO(
                                "Teclado",
                                "Teclado mecânico",
                                new BigDecimal("150.00"),
                                10,
                                null
                        )
                ),
                Arguments.of(
                        "SKU vazio",
                        new ProductRequestDTO(
                                "Teclado",
                                "Teclado mecânico",
                                new BigDecimal("150.00"),
                                10,
                                ""
                        )
                ),
                Arguments.of(
                        "SKU em branco",
                        new ProductRequestDTO(
                                "Teclado",
                                "Teclado mecânico",
                                new BigDecimal("150.00"),
                                10,
                                "   "
                        )
                ),
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

    static Stream<Arguments> invalidDetails() {
        return Stream.of(
                Arguments.of("Nome nulo", new ProductUpdateDetailsRequestDTO(null, "Descrição")),
                Arguments.of("Nome vazio", new ProductUpdateDetailsRequestDTO("", "Descrição")),
                Arguments.of("Nome em branco", new ProductUpdateDetailsRequestDTO("   ", "Descrição")),
                Arguments.of("Descrição nula", new ProductUpdateDetailsRequestDTO("Teclado", null)),
                Arguments.of("Descrição vazia", new ProductUpdateDetailsRequestDTO("Teclado", "")),
                Arguments.of("Descrição em branco", new ProductUpdateDetailsRequestDTO("Teclado", "   "))
        );
    }

    static Stream<String> patchEndpoints() {
        return Stream.of(
                "/products/{id}/stock?newStock=20",
                "/products/{id}/price?newPrice=200.00",
                "/products/{id}",
                "/products/{id}/activate",
                "/products/{id}/deactivate"
        );
    }

    static Stream<Arguments> writeEndpoints() {
        return Stream.concat(
                Stream.of(Arguments.of("POST", "/products")),
                patchEndpoints().map(endpoint -> Arguments.of("PATCH", endpoint))
        );
    }

    private Product saveProduct(int stock) {
        return productRepository.save(new Product(
                "Teclado", "Teclado mecânico", new BigDecimal("150.00"), stock, "TEST-KEYBOARD"
        ));
    }
}
