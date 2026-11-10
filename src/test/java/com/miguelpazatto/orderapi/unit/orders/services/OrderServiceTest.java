package com.miguelpazatto.orderapi.unit.orders.services;

import com.miguelpazatto.orderapi.auth.entities.User;
import com.miguelpazatto.orderapi.auth.entities.enums.UserRole;
import com.miguelpazatto.orderapi.core.config.RabbitMQConfig;
import com.miguelpazatto.orderapi.core.exceptions.BusinessRuleException;
import com.miguelpazatto.orderapi.core.exceptions.ResourceNotFoundException;
import com.miguelpazatto.orderapi.core.services.EmailService;
import com.miguelpazatto.orderapi.customers.entities.Customer;
import com.miguelpazatto.orderapi.customers.services.CustomerService;
import com.miguelpazatto.orderapi.orders.dtos.*;
import com.miguelpazatto.orderapi.orders.entities.Order;
import com.miguelpazatto.orderapi.orders.entities.enums.OrderStatus;
import com.miguelpazatto.orderapi.orders.repositories.OrderRepository;
import com.miguelpazatto.orderapi.orders.services.OrderService;
import com.miguelpazatto.orderapi.payments.entities.enums.PaymentStatus;
import com.miguelpazatto.orderapi.products.entities.Product;
import com.miguelpazatto.orderapi.products.services.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @InjectMocks private OrderService orderService;
    @Mock private OrderRepository orderRepository;
    @Mock private CustomerService customerService;
    @Mock private ProductService productService;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private EmailService emailService;

    private User user;
    private Customer customer;
    private Product product;

    @BeforeEach
    void setUp() {
        user = new User("cliente@test.com", "senha", Set.of(UserRole.CUSTOMER));
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        customer = new Customer(user.getId(), "Cliente", user.getEmail(), "(11) 99999-9999");
        ReflectionTestUtils.setField(customer, "id", UUID.randomUUID());
        product = new Product("Teclado", "Mecânico", new BigDecimal("10.25"), 10, "SKU-1");
        ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
        authenticate(user);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldListAllOrdersForAdmin() {
        authenticate(new User("admin@test.com", "senha", Set.of(UserRole.ADMIN)));
        when(orderRepository.findAll()).thenReturn(List.of(order(OrderStatus.WAITING_PAYMENT)));
        assertThat(orderService.findAll()).hasSize(1);
        verifyNoInteractions(customerService);
    }

    @Test
    void shouldListOnlyCustomerOrders() {
        when(customerService.findByUserId(user.getId())).thenReturn(customer);
        when(orderRepository.findByCustomerId(customer.getId())).thenReturn(List.of(order(OrderStatus.PAID)));
        assertThat(orderService.findAll()).hasSize(1);
        verify(orderRepository, never()).findAll();
    }

    @Test
    void shouldReturnEmptyListWhenCustomerHasNoOrders() {
        when(customerService.findByUserId(user.getId())).thenReturn(customer);
        when(orderRepository.findByCustomerId(customer.getId())).thenReturn(List.of());
        assertThat(orderService.findAll()).isEmpty();
    }

    @Test
    void shouldRejectListingWhenCustomerProfileIsMissing() {
        when(customerService.findByUserId(user.getId())).thenThrow(new ResourceNotFoundException("Perfil inexistente"));
        assertThatThrownBy(orderService::findAll).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void shouldFindOwnedOrder() {
        Order order = stubOrder(OrderStatus.PAID);
        when(customerService.findByUserId(user.getId())).thenReturn(customer);
        assertThat(orderService.findById(order.getId()).id()).isEqualTo(order.getId());
    }

    @Test
    void shouldAllowAdminToFindAnyOrder() {
        Order order = stubOrder(OrderStatus.PAID);
        authenticate(new User("admin@test.com", "senha", Set.of(UserRole.ADMIN)));
        assertThat(orderService.findById(order.getId()).id()).isEqualTo(order.getId());
        verifyNoInteractions(customerService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"find", "cancel"})
    void shouldRejectAccessToAnotherCustomersOrder(String operation) {
        Order order = stubOrder(OrderStatus.WAITING_PAYMENT);
        ReflectionTestUtils.setField(order, "customerId", UUID.randomUUID());
        when(customerService.findByUserId(user.getId())).thenReturn(customer);
        assertThatThrownBy(() -> invoke(operation, order.getId())).isInstanceOf(AccessDeniedException.class);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(productService, rabbitTemplate, emailService);
    }

    @Test
    void shouldCreateOrderAndGroupRepeatedProducts() {
        stubCreation();
        OrderRequestDTO request = new OrderRequestDTO(customer.getId(), List.of(
                new OrderItemRequestDTO(product.getId(), 2), new OrderItemRequestDTO(product.getId(), 3)));

        OrderResponseDTO result = orderService.insert(request);

        assertThat(result.totalPrice()).isEqualByComparingTo("51.25");
        assertThat(result.orderItemList()).singleElement().satisfies(item -> assertThat(item.quantity()).isEqualTo(5));
        assertThat(product.getAvailableStock()).isEqualTo(5);
        verify(productService).save(product);
        ArgumentCaptor<OrderCreatedEventDTO> event = ArgumentCaptor.forClass(OrderCreatedEventDTO.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE_PEDIDOS),
                eq(RabbitMQConfig.ROTA_PEDIDO_CRIADO), event.capture());
        assertThat(event.getValue().orderId()).isEqualTo(result.id());
        assertThat(event.getValue().customerId()).isEqualTo(customer.getId());
        assertThat(event.getValue().totalPrice()).isEqualByComparingTo("51.25");
        verify(rabbitTemplate).convertAndSend(RabbitMQConfig.EXCHANGE_DLX, RabbitMQConfig.ROTA_ESPERA_AVISO, result.id());
        verify(rabbitTemplate).convertAndSend(RabbitMQConfig.EXCHANGE_DLX, RabbitMQConfig.ROTA_ESPERA, result.id());
        verifyNoMoreInteractions(rabbitTemplate);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 11, 1000})
    void shouldRejectInvalidOrUnavailableQuantity(int quantity) {
        when(customerService.findEntityById(customer.getId())).thenReturn(customer);
        when(customerService.findByUserId(user.getId())).thenReturn(customer);
        when(productService.findEntityById(product.getId())).thenReturn(product);
        assertThatThrownBy(() -> orderService.insert(request(quantity))).isInstanceOf(BusinessRuleException.class);
        verify(orderRepository, never()).save(any());
        verify(productService, never()).save(any());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void shouldRejectCreationForAnotherCustomer() {
        when(customerService.findEntityById(customer.getId())).thenReturn(customer);
        Customer another = new Customer(user.getId(), "Outro", "outro@test.com", "(11) 99999-9999");
        ReflectionTestUtils.setField(another, "id", UUID.randomUUID());
        when(customerService.findByUserId(user.getId())).thenReturn(another);
        assertThatThrownBy(() -> orderService.insert(request(1))).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(productService, orderRepository, rabbitTemplate);
    }

    @Test
    void shouldRejectInactiveCustomer() {
        customer.deactivate();
        when(customerService.findEntityById(customer.getId())).thenReturn(customer);
        when(customerService.findByUserId(user.getId())).thenReturn(customer);
        assertThatThrownBy(() -> orderService.insert(request(1))).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(productService, orderRepository, rabbitTemplate);
    }

    @Test
    void shouldRejectInactiveProduct() {
        product.deactivate();
        when(customerService.findEntityById(customer.getId())).thenReturn(customer);
        when(customerService.findByUserId(user.getId())).thenReturn(customer);
        when(productService.findEntityById(product.getId())).thenReturn(product);
        assertThatThrownBy(() -> orderService.insert(request(1))).isInstanceOf(BusinessRuleException.class);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void shouldRejectMissingCustomerOnCreation() {
        when(customerService.findEntityById(customer.getId())).thenThrow(new ResourceNotFoundException("Cliente inexistente"));
        assertThatThrownBy(() -> orderService.insert(request(1))).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(productService, orderRepository, rabbitTemplate);
    }

    @Test
    void shouldRejectMissingProductOnCreation() {
        when(customerService.findEntityById(customer.getId())).thenReturn(customer);
        when(customerService.findByUserId(user.getId())).thenReturn(customer);
        when(productService.findEntityById(product.getId())).thenThrow(new ResourceNotFoundException("Produto inexistente"));
        assertThatThrownBy(() -> orderService.insert(request(1))).isInstanceOf(ResourceNotFoundException.class);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void shouldCancelOrderAndRestoreStock() {
        Order order = stubOrder(OrderStatus.WAITING_PAYMENT);
        when(customerService.findByUserId(user.getId())).thenReturn(customer);
        when(productService.findEntityById(product.getId())).thenReturn(product);
        when(orderRepository.save(order)).thenReturn(order);
        assertThat(orderService.cancelOrder(order.getId()).orderStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(product.getAvailableStock()).isEqualTo(12);
        verify(productService).save(product);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"SHIPPED", "DELIVERED", "CANCELED"})
    void shouldRejectInvalidCancellationWithoutRestoringStock(OrderStatus status) {
        Order order = stubOrder(status);
        when(customerService.findByUserId(user.getId())).thenReturn(customer);
        assertThatThrownBy(() -> orderService.cancelOrder(order.getId())).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(productService);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldShipPaidOrderAndNotifyDependencies() {
        Order order = stubOrder(OrderStatus.PAID);
        assertThat(orderService.shipOrder(order.getId()).orderStatus()).isEqualTo(OrderStatus.SHIPPED);
        verify(emailService).enviarEmailPedidoEnviado(order.getId(), customer.getEmail());
        verify(rabbitTemplate).convertAndSend(RabbitMQConfig.EXCHANGE_PEDIDOS,
                RabbitMQConfig.ROTA_PEDIDO_DESPACHADO, new OrderDispatchedEventDTO(order.getId()));
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PAID", mode = EnumSource.Mode.EXCLUDE)
    void shouldRejectInvalidShippingWithoutNotifying(OrderStatus status) {
        Order order = stubOrder(status);
        assertThatThrownBy(() -> orderService.shipOrder(order.getId())).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(emailService, rabbitTemplate);
    }

    @Test
    void shouldDeliverShippedOrderAndSendEmail() {
        Order order = stubOrder(OrderStatus.SHIPPED);
        when(orderRepository.save(order)).thenReturn(order);
        assertThat(orderService.deliverOrder(order.getId()).orderStatus()).isEqualTo(OrderStatus.DELIVERED);
        verify(emailService).enviarEmailConfirmacaoEntrega(order.getId(), customer.getEmail());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "SHIPPED", mode = EnumSource.Mode.EXCLUDE)
    void shouldRejectInvalidDeliveryWithoutEmail(OrderStatus status) {
        Order order = stubOrder(status);
        assertThatThrownBy(() -> orderService.deliverOrder(order.getId())).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(emailService);
        verify(orderRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void shouldApplyPaymentStatus(PaymentStatus status) {
        Order order = stubOrder(OrderStatus.WAITING_PAYMENT);
        orderService.atualizarStatusPagamento(order.getId(), status);
        OrderStatus expected = switch (status) {
            case PENDING -> OrderStatus.WAITING_PAYMENT;
            case APPROVED -> OrderStatus.PAID;
            case REJECTED -> OrderStatus.PAYMENT_FAILED;
        };
        assertThat(order.getOrderStatus()).isEqualTo(expected);
        verify(orderRepository).save(order);
    }

    @Test
    void shouldRejectApprovalOfCanceledOrder() {
        Order order = stubOrder(OrderStatus.CANCELED);
        assertThatThrownBy(() -> orderService.atualizarStatusPagamento(order.getId(), PaymentStatus.APPROVED))
                .isInstanceOf(BusinessRuleException.class);
        verify(orderRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"WAITING_PAYMENT", "PAYMENT_FAILED"})
    void shouldExpirePendingOrderWithoutAuthentication(OrderStatus status) {
        Order order = stubOrder(status);
        SecurityContextHolder.clearContext();
        when(productService.findEntityById(product.getId())).thenReturn(product);
        when(orderRepository.save(order)).thenReturn(order);
        orderService.cancelarPedidoExpirado(order.getId());
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(product.getAvailableStock()).isEqualTo(12);
        verify(emailService).enviarEmailCancelamentoPorInatividade(order.getId(), customer.getEmail());
        verifyNoInteractions(customerService);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PAID", "SHIPPED", "DELIVERED", "CANCELED"})
    void shouldIgnoreExpirationAndWarningForNonPendingOrder(OrderStatus status) {
        Order order = stubOrder(status);
        orderService.cancelarPedidoExpirado(order.getId());
        orderService.alertarPedidoQuaseExpirado(order.getId());
        assertThat(order.getOrderStatus()).isEqualTo(status);
        verifyNoInteractions(productService, emailService);
        verify(orderRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"WAITING_PAYMENT", "PAYMENT_FAILED"})
    void shouldWarnPendingOrder(OrderStatus status) {
        Order order = stubOrder(status);
        orderService.alertarPedidoQuaseExpirado(order.getId());
        verify(emailService).enviarEmailAvisoExpiracao(order.getId(), customer.getEmail());
        verify(orderRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"find", "cancel", "ship", "deliver", "payment", "expire", "warn"})
    void shouldRejectNonexistentOrder(String operation) {
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> invoke(operation, id)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(productService, rabbitTemplate, emailService);
    }

    private void invoke(String operation, UUID id) {
        switch (operation) {
            case "find" -> orderService.findById(id);
            case "cancel" -> orderService.cancelOrder(id);
            case "ship" -> orderService.shipOrder(id);
            case "deliver" -> orderService.deliverOrder(id);
            case "payment" -> orderService.atualizarStatusPagamento(id, PaymentStatus.APPROVED);
            case "expire" -> orderService.cancelarPedidoExpirado(id);
            case "warn" -> orderService.alertarPedidoQuaseExpirado(id);
            default -> throw new IllegalArgumentException(operation);
        }
    }

    private void stubCreation() {
        when(customerService.findEntityById(customer.getId())).thenReturn(customer);
        when(customerService.findByUserId(user.getId())).thenReturn(customer);
        when(productService.findEntityById(product.getId())).thenReturn(product);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
            return order;
        });
    }

    private OrderRequestDTO request(int quantity) {
        return new OrderRequestDTO(customer.getId(), List.of(new OrderItemRequestDTO(product.getId(), quantity)));
    }

    private Order stubOrder(OrderStatus status) {
        Order order = order(status);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        return order;
    }

    private Order order(OrderStatus status) {
        Order order = new Order(customer.getId(), customer.getName(), customer.getEmail(), List.of(
                new Order.ItemData(product.getId(), product.getName(), 2, product.getPrice())));
        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
        switch (status) {
            case WAITING_PAYMENT -> { }
            case PAYMENT_FAILED -> order.markAsPaymentFailed();
            case PAID -> order.markAsPaid();
            case SHIPPED -> { order.markAsPaid(); order.dispatch(); }
            case DELIVERED -> { order.markAsPaid(); order.dispatch(); order.deliver(); }
            case CANCELED -> order.cancel();
        }
        return order;
    }

    private void authenticate(User principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
