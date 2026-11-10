package com.miguelpazatto.orderapi.integration.payments;

import com.miguelpazatto.orderapi.core.config.RabbitMQConfig;
import com.miguelpazatto.orderapi.core.exceptions.ResourceNotFoundException;
import com.miguelpazatto.orderapi.integration.infra.AbstractIntegrationTest;
import com.miguelpazatto.orderapi.orders.entities.Order;
import com.miguelpazatto.orderapi.orders.repositories.OrderRepository;
import com.miguelpazatto.orderapi.payments.dtos.PaymentProcessedEventDTO;
import com.miguelpazatto.orderapi.payments.entities.Payment;
import com.miguelpazatto.orderapi.payments.entities.enums.PaymentStatus;
import com.miguelpazatto.orderapi.payments.repositories.PaymentRepository;
import com.miguelpazatto.orderapi.payments.services.PaymentService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentIntegrationTest extends AbstractIntegrationTest {
    @Autowired
    private PaymentService service;
    @Autowired
    private PaymentRepository payments;
    @Autowired
    private OrderRepository orders;
    @MockBean
    private RabbitTemplate rabbit;
    private Order order;
    private Payment payment;

    @BeforeEach
    void setUp() {
        clearInvocations(rabbit);
        payments.deleteAll(); orders.deleteAll();
        order = orders.save(new Order(UUID.randomUUID(), "Cliente", "user@test.com", List.of(
                new Order.ItemData(UUID.randomUUID(), "Produto", 1, BigDecimal.TEN))));
        payment = payments.save(new Payment(order.getId(), BigDecimal.TEN));
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldPersistAndPublishPaymentWithoutLogin(boolean approved) {
        if (approved) service.processarPagamentoAprovado(payment.getId()); else service.processarPagamentoRecusado(payment.getId());
        PaymentStatus expected = approved ? PaymentStatus.APPROVED : PaymentStatus.REJECTED;
        assertThat(payments.findById(payment.getId()).orElseThrow().getPaymentStatus()).isEqualTo(expected);
        verify(rabbit).convertAndSend(RabbitMQConfig.EXCHANGE_PAGAMENTO_CONCLUIDO, "",
                new PaymentProcessedEventDTO(order.getId(), expected.name(), order.getCustomerEmail()));
    }

    @Test
    void shouldRollBackStatusWhenOrderIsMissing() {
        orders.deleteAll();
        assertThatThrownBy(() -> service.processarPagamentoAprovado(payment.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThat(payments.findById(payment.getId()).orElseThrow().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        verifyNoInteractions(rabbit);
    }

    @Test
    void shouldRollBackStatusWhenPublishingFails() {
        doThrow(new AmqpException("offline")).when(rabbit).convertAndSend(eq(RabbitMQConfig.EXCHANGE_PAGAMENTO_CONCLUIDO),
                eq(""), any(PaymentProcessedEventDTO.class));
        assertThatThrownBy(() -> service.processarPagamentoRecusado(payment.getId())).isInstanceOf(AmqpException.class);
        assertThat(payments.findById(payment.getId()).orElseThrow().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }
}
