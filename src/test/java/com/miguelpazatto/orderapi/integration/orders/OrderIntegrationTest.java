package com.miguelpazatto.orderapi.integration.orders;

import com.miguelpazatto.orderapi.auth.entities.User;
import com.miguelpazatto.orderapi.core.config.RabbitMQConfig;
import com.miguelpazatto.orderapi.customers.entities.Customer;
import com.miguelpazatto.orderapi.customers.repositories.CustomerRepository;
import com.miguelpazatto.orderapi.integration.infra.AbstractIntegrationTest;
import com.miguelpazatto.orderapi.integration.infra.TestUserFactory;
import com.miguelpazatto.orderapi.orders.dtos.*;
import com.miguelpazatto.orderapi.orders.entities.Order;
import com.miguelpazatto.orderapi.orders.entities.enums.OrderStatus;
import com.miguelpazatto.orderapi.orders.repositories.OrderRepository;
import com.miguelpazatto.orderapi.orders.services.OrderService;
import com.miguelpazatto.orderapi.payments.entities.enums.PaymentStatus;
import com.miguelpazatto.orderapi.products.entities.Product;
import com.miguelpazatto.orderapi.products.entities.enums.ProductStatus;
import com.miguelpazatto.orderapi.products.repositories.ProductRepository;
import com.miguelpazatto.orderapi.products.services.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderIntegrationTest extends AbstractIntegrationTest {

    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private TestUserFactory testUserFactory;
    @Autowired private OrderService orderService;
    @MockBean private RabbitTemplate rabbitTemplate;
    @SpyBean private ProductService productService;

    private User user;
    private Customer customer;
    private Product product;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        user = testUserFactory.createCustomer();
        customer = saveCustomer(user);
        product = productRepository.save(new Product("Teclado", "Mecânico", new BigDecimal("10.25"), 10, "SKU-1"));
    }

    @AfterEach
    void cleanDatabase() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        customerRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldListAllOrdersForAdmin() {
        Order own = saveOrder(customer, OrderStatus.WAITING_PAYMENT);
        Customer another = saveCustomer(testUserFactory.createCustomer());
        Order other = saveOrder(another, OrderStatus.PAID);

        given().spec(authenticatedSpec(testUserFactory.createAdmin()))
        .when().get("/orders")
        .then().log().ifValidationFails()
                .statusCode(HttpStatus.OK.value())
                .body("id", containsInAnyOrder(own.getId().toString(), other.getId().toString()));
    }

    @Test
    void shouldListOnlyAuthenticatedCustomersOrders() {
        Order own = saveOrder(customer, OrderStatus.WAITING_PAYMENT);
        saveOrder(saveCustomer(testUserFactory.createCustomer()), OrderStatus.PAID);

        given().spec(authenticatedSpec(user))
        .when().get("/orders")
        .then().log().ifValidationFails()
                .statusCode(HttpStatus.OK.value())
                .body("id", org.hamcrest.Matchers.contains(own.getId().toString()));
    }

    @Test
    void shouldReturnEmptyListWhenCustomerHasNoOrders() {
        given().spec(authenticatedSpec(user))
        .when().get("/orders")
        .then().log().ifValidationFails()
                .statusCode(HttpStatus.OK.value()).body("size()", equalTo(0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldFindOrderForOwnerOrAdmin(boolean admin) {
        Order order = saveOrder(customer, OrderStatus.WAITING_PAYMENT);
        User requester = admin ? testUserFactory.createAdmin() : user;

        given().spec(authenticatedSpec(requester))
        .when().get("/orders/{id}", order.getId())
        .then().log().ifValidationFails()
                .statusCode(HttpStatus.OK.value())
                .body("id", equalTo(order.getId().toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PATCH"})
    void shouldForbidReadingOrCancelingAnotherCustomersOrder(String method) {
        Order order = saveOrder(saveCustomer(testUserFactory.createCustomer()), OrderStatus.WAITING_PAYMENT);
        String endpoint = "/orders/" + order.getId() + (method.equals("PATCH") ? "/cancel" : "");

        given().spec(authenticatedSpec(user))
        .when().request(method, endpoint)
        .then().log().ifValidationFails().statusCode(HttpStatus.FORBIDDEN.value());

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getOrderStatus())
                .isEqualTo(OrderStatus.WAITING_PAYMENT);
    }

    @Test
    void shouldCreateOrderWithGroupedItemsAndPublishEvents() {
        OrderRequestDTO request = new OrderRequestDTO(customer.getId(), List.of(
                new OrderItemRequestDTO(product.getId(), 2), new OrderItemRequestDTO(product.getId(), 3)));

        String id = given().spec(authenticatedSpec(user)).body(request)
        .when().post("/orders")
        .then().log().ifValidationFails()
                .statusCode(HttpStatus.CREATED.value())
                .body("orderStatus", equalTo("WAITING_PAYMENT"))
                .body("totalPrice", equalTo(51.25f))
                .body("orderItemList.size()", equalTo(1))
                .body("orderItemList[0].quantity", equalTo(5))
                .extract().path("id");

        UUID orderId = UUID.fromString(id);
        assertThat(orderRepository.findById(orderId)).isPresent();
        assertThat(stock()).isEqualTo(5);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE_PEDIDOS),
                eq(RabbitMQConfig.ROTA_PEDIDO_CRIADO), argThat((OrderCreatedEventDTO event) ->
                        event.orderId().equals(orderId) && event.totalPrice().compareTo(new BigDecimal("51.25")) == 0));
        verify(rabbitTemplate).convertAndSend(RabbitMQConfig.EXCHANGE_DLX, RabbitMQConfig.ROTA_ESPERA_AVISO, orderId);
        verify(rabbitTemplate).convertAndSend(RabbitMQConfig.EXCHANGE_DLX, RabbitMQConfig.ROTA_ESPERA, orderId);
    }

    @Test
    void shouldCreateOrderWithMultipleProductsAndExactTotal() {
        Product second = productRepository.save(new Product("Mouse", "Óptico", new BigDecimal("0.10"), 10, "SKU-2"));
        OrderRequestDTO request = new OrderRequestDTO(customer.getId(), List.of(
                new OrderItemRequestDTO(product.getId(), 2), new OrderItemRequestDTO(second.getId(), 3)));

        given().spec(authenticatedSpec(user)).body(request)
        .when().post("/orders")
        .then().log().ifValidationFails().statusCode(HttpStatus.CREATED.value())
                .body("totalPrice", equalTo(20.8f)).body("orderItemList.size()", equalTo(2));
        assertThat(stock()).isEqualTo(8);
        assertThat(productRepository.findById(second.getId()).orElseThrow().getAvailableStock()).isEqualTo(7);
    }

    @Test
    void shouldAllowAdminToCreateOrderForCustomer() {
        given().spec(authenticatedSpec(testUserFactory.createAdmin())).body(request(1))
        .when().post("/orders")
        .then().log().ifValidationFails().statusCode(HttpStatus.CREATED.value());
    }

    @Test
    void shouldBuyExactlyAvailableStock() {
        given().spec(authenticatedSpec(user)).body(request(10))
        .when().post("/orders")
        .then().log().ifValidationFails().statusCode(HttpStatus.CREATED.value());

        Product updated = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updated.getAvailableStock()).isZero();
        assertThat(updated.getProductStatus()).isEqualTo(ProductStatus.OUT_OF_STOCK);
    }

    @Test
    void shouldAcceptMaximumQuantity() {
        product.updateStock(999);
        productRepository.save(product);
        given().spec(authenticatedSpec(user)).body(request(999))
        .when().post("/orders")
        .then().log().ifValidationFails().statusCode(HttpStatus.CREATED.value());
        assertThat(stock()).isZero();
    }

    @ParameterizedTest(name = "{index} - {0}")
    @MethodSource("invalidRequests")
    void shouldRejectInvalidCreationData(String scenario, String json) {
        String body = json.replace("CUSTOMER_ID", customer.getId().toString())
                .replace("PRODUCT_ID", product.getId().toString());
        given().spec(authenticatedSpec(user)).body(body)
        .when().post("/orders")
        .then().log().ifValidationFails().statusCode(HttpStatus.BAD_REQUEST.value());
        assertThat(orderRepository.count()).isZero();
        assertThat(stock()).isEqualTo(10);
        verifyNoInteractions(rabbitTemplate);
    }

    @ParameterizedTest
    @ValueSource(strings = {"customer", "product"})
    void shouldRejectCreationWithNonexistentReference(String reference) {
        OrderRequestDTO request = new OrderRequestDTO(
                reference.equals("customer") ? UUID.randomUUID() : customer.getId(),
                List.of(new OrderItemRequestDTO(reference.equals("product") ? UUID.randomUUID() : product.getId(), 1)));
        given().spec(authenticatedSpec(user)).body(request)
        .when().post("/orders")
        .then().log().ifValidationFails().statusCode(HttpStatus.NOT_FOUND.value());
        assertThat(orderRepository.count()).isZero();
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void shouldForbidCreationForAnotherCustomer() {
        Customer other = saveCustomer(testUserFactory.createCustomer());
        given().spec(authenticatedSpec(user))
                .body(new OrderRequestDTO(other.getId(), request(1).items()))
        .when().post("/orders")
        .then().log().ifValidationFails().statusCode(HttpStatus.FORBIDDEN.value());
        assertThat(orderRepository.count()).isZero();
        assertThat(stock()).isEqualTo(10);
    }

    @ParameterizedTest
    @ValueSource(strings = {"customer", "product"})
    void shouldRejectInactiveCustomerOrProduct(String inactive) {
        if (inactive.equals("customer")) {
            customer.deactivate();
            customerRepository.save(customer);
        } else {
            product.deactivate();
            productRepository.save(product);
        }
        given().spec(authenticatedSpec(user)).body(request(1))
        .when().post("/orders")
        .then().log().ifValidationFails().statusCode(HttpStatus.BAD_REQUEST.value());
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void shouldRejectGroupedQuantityAboveLimit() {
        product.updateStock(2000);
        productRepository.save(product);
        OrderRequestDTO request = new OrderRequestDTO(customer.getId(), List.of(
                new OrderItemRequestDTO(product.getId(), 600), new OrderItemRequestDTO(product.getId(), 600)));
        given().spec(authenticatedSpec(user)).body(request)
        .when().post("/orders")
        .then().log().ifValidationFails().statusCode(HttpStatus.BAD_REQUEST.value());
        assertThat(stock()).isEqualTo(2000);
    }

    @Test
    void shouldRejectRepeatedItemsWhoseSumExceedsStock() {
        OrderRequestDTO request = new OrderRequestDTO(customer.getId(), List.of(
                new OrderItemRequestDTO(product.getId(), 6), new OrderItemRequestDTO(product.getId(), 6)));
        given().spec(authenticatedSpec(user)).body(request)
        .when().post("/orders")
        .then().log().ifValidationFails().statusCode(HttpStatus.BAD_REQUEST.value());
        assertThat(stock()).isEqualTo(10);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void shouldRollBackEarlierStockChangeWhenLaterItemFails() {
        Product second = productRepository.save(new Product("Mouse", "Óptico", new BigDecimal("5.00"), 1, "SKU-2"));
        OrderRequestDTO request = new OrderRequestDTO(customer.getId(), List.of(
                new OrderItemRequestDTO(product.getId(), 2), new OrderItemRequestDTO(second.getId(), 2)));
        given().spec(authenticatedSpec(user)).body(request)
        .when().post("/orders")
        .then().log().ifValidationFails().statusCode(HttpStatus.BAD_REQUEST.value());
        verify(productService).save(argThat(saved -> saved.getId().equals(product.getId())));
        assertThat(stock()).isEqualTo(10);
        assertThat(productRepository.findById(second.getId()).orElseThrow().getAvailableStock()).isEqualTo(1);
        assertThat(orderRepository.count()).isZero();
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void shouldRollBackOrderAndStockWhenPublishingFails() {
        doThrow(new AmqpException("Broker indisponível"))
                .when(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE_PEDIDOS),
                        eq(RabbitMQConfig.ROTA_PEDIDO_CRIADO), any(OrderCreatedEventDTO.class));
        given().spec(authenticatedSpec(user)).body(request(2))
        .when().post("/orders")
        .then().log().ifValidationFails().statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(orderRepository.count()).isZero();
        assertThat(stock()).isEqualTo(10);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"WAITING_PAYMENT", "PAYMENT_FAILED", "PAID"})
    void shouldCancelEligibleOrderAndRestoreStock(OrderStatus status) {
        Order order = saveOrder(customer, status);
        given().spec(authenticatedSpec(user))
        .when().patch("/orders/{id}/cancel", order.getId())
        .then().log().ifValidationFails().statusCode(HttpStatus.OK.value())
                .body("orderStatus", equalTo("CANCELED"));
        assertThat(stock()).isEqualTo(10);
        assertThat(persistedStatus(order)).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    void shouldAllowAdminToCancelCustomerOrder() {
        Order order = saveOrder(customer, OrderStatus.WAITING_PAYMENT);
        given().spec(authenticatedSpec(testUserFactory.createAdmin()))
        .when().patch("/orders/{id}/cancel", order.getId())
        .then().log().ifValidationFails().statusCode(HttpStatus.OK.value());
        assertThat(stock()).isEqualTo(10);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"SHIPPED", "DELIVERED", "CANCELED"})
    void shouldRejectInvalidCancellationWithoutChangingStock(OrderStatus status) {
        Order order = saveOrder(customer, status);
        int before = stock();
        given().spec(authenticatedSpec(user))
        .when().patch("/orders/{id}/cancel", order.getId())
        .then().log().ifValidationFails().statusCode(HttpStatus.BAD_REQUEST.value());
        assertThat(stock()).isEqualTo(before);
        assertThat(persistedStatus(order)).isEqualTo(status);
    }

    @Test
    void shouldShipPaidOrderAsAdmin() {
        Order order = saveOrder(customer, OrderStatus.PAID);
        given().spec(authenticatedSpec(testUserFactory.createAdmin()))
        .when().patch("/orders/{id}/ship", order.getId())
        .then().log().ifValidationFails().statusCode(HttpStatus.OK.value())
                .body("orderStatus", equalTo("SHIPPED"));
        assertThat(persistedStatus(order)).isEqualTo(OrderStatus.SHIPPED);
        verify(emailService).enviarEmailPedidoEnviado(order.getId(), customer.getEmail());
        verify(rabbitTemplate).convertAndSend(RabbitMQConfig.EXCHANGE_PEDIDOS,
                RabbitMQConfig.ROTA_PEDIDO_DESPACHADO, new OrderDispatchedEventDTO(order.getId()));
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PAID", mode = EnumSource.Mode.EXCLUDE)
    void shouldRejectShippingInInvalidStatus(OrderStatus status) {
        Order order = saveOrder(customer, status);
        given().spec(authenticatedSpec(testUserFactory.createAdmin()))
        .when().patch("/orders/{id}/ship", order.getId())
        .then().log().ifValidationFails().statusCode(HttpStatus.BAD_REQUEST.value());
        verifyNoInteractions(emailService, rabbitTemplate);
    }

    @Test
    void shouldDeliverShippedOrderAsAdmin() {
        Order order = saveOrder(customer, OrderStatus.SHIPPED);
        given().spec(authenticatedSpec(testUserFactory.createAdmin()))
        .when().patch("/orders/{id}/deliver", order.getId())
        .then().log().ifValidationFails().statusCode(HttpStatus.OK.value())
                .body("orderStatus", equalTo("DELIVERED"));
        assertThat(persistedStatus(order)).isEqualTo(OrderStatus.DELIVERED);
        verify(emailService).enviarEmailConfirmacaoEntrega(order.getId(), customer.getEmail());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "SHIPPED", mode = EnumSource.Mode.EXCLUDE)
    void shouldRejectDeliveryInInvalidStatus(OrderStatus status) {
        Order order = saveOrder(customer, status);
        given().spec(authenticatedSpec(testUserFactory.createAdmin()))
        .when().patch("/orders/{id}/deliver", order.getId())
        .then().log().ifValidationFails().statusCode(HttpStatus.BAD_REQUEST.value());
        verifyNoInteractions(emailService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ship", "deliver"})
    void shouldForbidAdministrativeOperationsForCustomer(String operation) {
        Order order = saveOrder(customer, operation.equals("ship") ? OrderStatus.PAID : OrderStatus.SHIPPED);
        given().spec(authenticatedSpec(user))
        .when().patch("/orders/{id}/{operation}", order.getId(), operation)
        .then().log().ifValidationFails().statusCode(HttpStatus.FORBIDDEN.value());
        verifyNoInteractions(emailService, rabbitTemplate);
    }

    @ParameterizedTest(name = "{index} - {0} {1}")
    @MethodSource("endpoints")
    void shouldForbidRequestsWithoutAuthentication(String method, String endpoint) {
        Order order = saveOrder(customer, OrderStatus.PAID);
        given().spec(requestSpecification).body(request(1))
        .when().request(method, endpoint.replace("{id}", order.getId().toString()))
        .then().log().ifValidationFails().statusCode(HttpStatus.FORBIDDEN.value());
    }

    @ParameterizedTest(name = "{index} - {0} {1}")
    @MethodSource("existingOrderEndpoints")
    void shouldReturnNotFoundWhenOrderDoesNotExist(String method, String endpoint) {
        given().spec(authenticatedSpec(testUserFactory.createAdmin()))
        .when().request(method, endpoint.replace("{id}", UUID.randomUUID().toString()))
        .then().log().ifValidationFails().statusCode(HttpStatus.NOT_FOUND.value());
    }

    @ParameterizedTest(name = "{index} - {0} {1}")
    @MethodSource("existingOrderEndpoints")
    void shouldRejectMalformedOrderId(String method, String endpoint) {
        given().spec(authenticatedSpec(testUserFactory.createAdmin()))
        .when().request(method, endpoint.replace("{id}", "invalid-uuid"))
        .then().log().ifValidationFails().statusCode(HttpStatus.BAD_REQUEST.value());
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void shouldPersistPaymentUpdateWithoutHttpAuthentication(PaymentStatus payment) {
        Order order = saveOrder(customer, OrderStatus.WAITING_PAYMENT);
        SecurityContextHolder.clearContext();
        orderService.atualizarStatusPagamento(order.getId(), payment);
        OrderStatus expected = switch (payment) {
            case PENDING -> OrderStatus.WAITING_PAYMENT;
            case APPROVED -> OrderStatus.PAID;
            case REJECTED -> OrderStatus.PAYMENT_FAILED;
        };
        assertThat(persistedStatus(order)).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"WAITING_PAYMENT", "PAYMENT_FAILED"})
    void shouldExpirePendingOrderWithoutHttpAuthentication(OrderStatus status) {
        Order order = saveOrder(customer, status);
        SecurityContextHolder.clearContext();
        orderService.cancelarPedidoExpirado(order.getId());
        orderService.cancelarPedidoExpirado(order.getId());
        assertThat(persistedStatus(order)).isEqualTo(OrderStatus.CANCELED);
        assertThat(stock()).isEqualTo(10);
        verify(emailService).enviarEmailCancelamentoPorInatividade(order.getId(), customer.getEmail());
    }

    static Stream<Arguments> invalidRequests() {
        String validItem = "{\"productId\":\"PRODUCT_ID\",\"quantity\":1}";
        Stream<Arguments> basics = Stream.of(
                Arguments.of("Cliente nulo", "{\"customerId\":null,\"items\":[" + validItem + "]}"),
                Arguments.of("Itens nulos", "{\"customerId\":\"CUSTOMER_ID\",\"items\":null}"),
                Arguments.of("Itens vazios", "{\"customerId\":\"CUSTOMER_ID\",\"items\":[]}"),
                Arguments.of("Item nulo", "{\"customerId\":\"CUSTOMER_ID\",\"items\":[null]}"),
                Arguments.of("Produto nulo", "{\"customerId\":\"CUSTOMER_ID\",\"items\":[{\"productId\":null,\"quantity\":1}]}"),
                Arguments.of("UUID inválido", "{\"customerId\":\"inválido\",\"items\":[" + validItem + "]}"),
                Arguments.of("JSON malformado", "{\"customerId\":"));
        Stream<Arguments> quantities = Stream.of("null", "0", "-1", "1000", "\"inválido\"")
                .map(quantity -> Arguments.of("Quantidade " + quantity,
                        "{\"customerId\":\"CUSTOMER_ID\",\"items\":[{\"productId\":\"PRODUCT_ID\",\"quantity\":" + quantity + "}]}"));
        return Stream.concat(basics, quantities);
    }

    static Stream<Arguments> existingOrderEndpoints() {
        return Stream.of(
                Arguments.of("GET", "/orders/{id}"),
                Arguments.of("PATCH", "/orders/{id}/cancel"),
                Arguments.of("PATCH", "/orders/{id}/ship"),
                Arguments.of("PATCH", "/orders/{id}/deliver"));
    }

    static Stream<Arguments> endpoints() {
        return Stream.concat(Stream.of(Arguments.of("GET", "/orders"), Arguments.of("POST", "/orders")),
                existingOrderEndpoints());
    }

    private Customer saveCustomer(User owner) {
        return customerRepository.save(new Customer(owner.getId(), "Cliente", owner.getEmail(), "(11) 99999-9999"));
    }

    private OrderRequestDTO request(int quantity) {
        return new OrderRequestDTO(customer.getId(), List.of(new OrderItemRequestDTO(product.getId(), quantity)));
    }

    private int stock() {
        return productRepository.findById(product.getId()).orElseThrow().getAvailableStock();
    }

    private OrderStatus persistedStatus(Order order) {
        return orderRepository.findById(order.getId()).orElseThrow().getOrderStatus();
    }

    private Order saveOrder(Customer owner, OrderStatus status) {
        Order order = new Order(owner.getId(), owner.getName(), owner.getEmail(), List.of(
                new Order.ItemData(product.getId(), product.getName(), 2, product.getPrice())));
        switch (status) {
            case WAITING_PAYMENT -> { }
            case PAYMENT_FAILED -> order.markAsPaymentFailed();
            case PAID -> order.markAsPaid();
            case SHIPPED -> { order.markAsPaid(); order.dispatch(); }
            case DELIVERED -> { order.markAsPaid(); order.dispatch(); order.deliver(); }
            case CANCELED -> order.cancel();
        }
        if (status != OrderStatus.CANCELED) {
            product = productRepository.findById(product.getId()).orElseThrow();
            product.decreaseStock(2);
            product = productRepository.save(product);
        }
        return orderRepository.save(order);
    }
}
