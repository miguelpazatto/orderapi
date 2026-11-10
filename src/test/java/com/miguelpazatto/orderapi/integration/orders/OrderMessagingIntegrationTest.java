package com.miguelpazatto.orderapi.integration.orders;

import com.miguelpazatto.orderapi.auth.entities.User;
import com.miguelpazatto.orderapi.core.config.RabbitMQConfig;
import com.miguelpazatto.orderapi.customers.entities.Customer;
import com.miguelpazatto.orderapi.customers.repositories.CustomerRepository;
import com.miguelpazatto.orderapi.integration.infra.AbstractIntegrationTest;
import com.miguelpazatto.orderapi.integration.infra.TestUserFactory;
import com.miguelpazatto.orderapi.orders.dtos.OrderCreatedEventDTO;
import com.miguelpazatto.orderapi.orders.dtos.OrderDispatchedEventDTO;
import com.miguelpazatto.orderapi.orders.dtos.OrderItemRequestDTO;
import com.miguelpazatto.orderapi.orders.dtos.OrderRequestDTO;
import com.miguelpazatto.orderapi.orders.entities.Order;
import com.miguelpazatto.orderapi.orders.repositories.OrderRepository;
import com.miguelpazatto.orderapi.products.entities.Product;
import com.miguelpazatto.orderapi.products.repositories.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

class OrderMessagingIntegrationTest extends AbstractIntegrationTest {

    @Container
    static final GenericContainer<?> rabbit = new GenericContainer<>(DockerImageName.parse("rabbitmq:4-alpine"))
            .withEnv("RABBITMQ_DEFAULT_USER", "integration")
            .withEnv("RABBITMQ_DEFAULT_PASS", "integration")
            .withExposedPorts(5672);

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", () -> rabbit.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "integration");
        registry.add("spring.rabbitmq.password", () -> "integration");
    }

    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private TestUserFactory testUserFactory;

    private Customer customer;
    private Product product;
    private User admin;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        customerRepository.deleteAll();
        admin = testUserFactory.createAdmin();
        User user = testUserFactory.createCustomer();
        customer = customerRepository.save(new Customer(user.getId(), "Cliente", user.getEmail(), "(11) 99999-9999"));
        product = productRepository.save(new Product("Teclado", "Mecânico", new BigDecimal("10.25"), 10, "SKU-RABBIT"));
        RabbitAdmin rabbitAdmin = new RabbitAdmin(rabbitTemplate.getConnectionFactory());
        rabbitAdmin.initialize();
        for (String queue : List.of(RabbitMQConfig.FILA_PAGAMENTO, RabbitMQConfig.FILA_ESPERA_AVISO_TTL,
                RabbitMQConfig.FILA_ESPERA_TTL, RabbitMQConfig.FILA_PEDIDO_DESPACHADO)) {
            rabbitAdmin.purgeQueue(queue);
        }
    }

    @Test
    void shouldRouteCreationAndExpirationMessagesToRealQueues() {
        OrderRequestDTO request = new OrderRequestDTO(customer.getId(),
                List.of(new OrderItemRequestDTO(product.getId(), 2)));
        String id = given().spec(authenticatedSpec(admin)).body(request)
        .when().post("/orders")
        .then().log().ifValidationFails().statusCode(201).extract().path("id");

        UUID orderId = UUID.fromString(id);
        Object payload = rabbitTemplate.receiveAndConvert(RabbitMQConfig.FILA_PAGAMENTO, 5000);
        assertThat(payload).isInstanceOfSatisfying(OrderCreatedEventDTO.class, event -> {
            assertThat(event.orderId()).isEqualTo(orderId);
            assertThat(event.customerId()).isEqualTo(customer.getId());
            assertThat(event.totalPrice()).isEqualByComparingTo("20.50");
        });
        assertThat(rabbitTemplate.receiveAndConvert(RabbitMQConfig.FILA_ESPERA_AVISO_TTL, 5000)).isEqualTo(orderId);
        assertThat(rabbitTemplate.receiveAndConvert(RabbitMQConfig.FILA_ESPERA_TTL, 5000)).isEqualTo(orderId);
    }

    @Test
    void shouldRouteDispatchEventToLogisticsQueue() {
        Order order = new Order(customer.getId(), customer.getName(), customer.getEmail(), List.of(
                new Order.ItemData(product.getId(), product.getName(), 1, product.getPrice())));
        order.markAsPaid();
        order = orderRepository.save(order);
        given().spec(authenticatedSpec(admin))
        .when().patch("/orders/{id}/ship", order.getId())
        .then().log().ifValidationFails().statusCode(200);

        assertThat(rabbitTemplate.receiveAndConvert(RabbitMQConfig.FILA_PEDIDO_DESPACHADO, 5000))
                .isEqualTo(new OrderDispatchedEventDTO(order.getId()));
    }
}
