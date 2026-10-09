package com.miguelpazatto.orderapi.integration.infra;

import com.miguelpazatto.orderapi.core.services.EmailService;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    EmailService emailService;

    @LocalServerPort
    protected int port;

    protected RequestSpecification requestSpecification;

    @BeforeEach
    protected void configureRestAssured() {
        requestSpecification = new RequestSpecBuilder()
                .setBaseUri("http://localhost")
                .setPort(port)
                .setAccept(ContentType.JSON)
                .build();
    }
}

