package com.miguelpazatto.orderapi.unit.payments.services;

import com.miguelpazatto.orderapi.core.config.RabbitMQConfig;
import com.miguelpazatto.orderapi.core.exceptions.ResourceNotFoundException;
import com.miguelpazatto.orderapi.orders.entities.Order;
import com.miguelpazatto.orderapi.orders.repositories.OrderRepository;
import com.miguelpazatto.orderapi.payments.dtos.PaymentProcessedEventDTO;
import com.miguelpazatto.orderapi.payments.entities.Payment;
import com.miguelpazatto.orderapi.payments.entities.enums.PaymentStatus;
import com.miguelpazatto.orderapi.payments.repositories.PaymentRepository;
import com.miguelpazatto.orderapi.payments.services.PaymentService;
import com.stripe.Stripe;
import com.stripe.exception.ApiConnectionException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock
    private PaymentRepository payments;
    @Mock
    private OrderRepository orders;
    @Mock
    private RabbitTemplate rabbit;
    @InjectMocks
    private PaymentService service;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldCreatePendingPaymentAndConvertAmountToCents(boolean stripeFails) throws Exception {
        UUID orderId = UUID.randomUUID();
        when(payments.save(any())).thenAnswer(call -> {
            Payment payment = call.getArgument(0);
            ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
            return payment;
        });
        ReflectionTestUtils.setField(service, "stripeApiKey", "sk_test_fake");
        String originalKey = Stripe.apiKey;
        try (MockedStatic<PaymentIntent> stripe = mockStatic(PaymentIntent.class)) {
            if (stripeFails) {
                stripe.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)))
                        .thenThrow(new ApiConnectionException("offline"));
            } else {
                PaymentIntent intent = new PaymentIntent();
                intent.setId("pi_test");
                stripe.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class))).thenReturn(intent);
            }
            assertThatCode(() -> service.processarPagamento(orderId, new BigDecimal("10.25"))).doesNotThrowAnyException();
            stripe.verify(() -> PaymentIntent.create(argThat((PaymentIntentCreateParams params) ->
                    params.getAmount() == 1025L && params.getCurrency().equals("brl") && params.getMetadata().containsKey("payment_id"))));
            ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
            verify(payments).save(payment.capture());
            assertThat(payment.getValue().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        } finally {
            Stripe.apiKey = originalKey;
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldPublishPaymentResultWithoutAuthentication(boolean approved) {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Payment payment = new Payment(orderId, new BigDecimal("10.25"));
        Order order = new Order(UUID.randomUUID(), "Cliente", "user@test.com", List.of(
                new Order.ItemData(UUID.randomUUID(), "Produto", 1, new BigDecimal("10.25"))));
        when(payments.findById(id)).thenReturn(Optional.of(payment));
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        if (approved) service.processarPagamentoAprovado(id); else service.processarPagamentoRecusado(id);
        PaymentStatus status = approved ? PaymentStatus.APPROVED : PaymentStatus.REJECTED;
        assertThat(payment.getPaymentStatus()).isEqualTo(status);
        verify(rabbit).convertAndSend(RabbitMQConfig.EXCHANGE_PAGAMENTO_CONCLUIDO, "",
                new PaymentProcessedEventDTO(orderId, status.name(), order.getCustomerEmail()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRejectMissingPayment(boolean approved) {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> {
            if (approved) service.processarPagamentoAprovado(id); else service.processarPagamentoRecusado(id);
        }).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(rabbit);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldNotPublishWhenOrderDoesNotExist(boolean approved) {
        UUID id = UUID.randomUUID();
        when(payments.findById(id)).thenReturn(Optional.of(new Payment(UUID.randomUUID(), BigDecimal.TEN)));
        assertThatThrownBy(() -> {
            if (approved) service.processarPagamentoAprovado(id); else service.processarPagamentoRecusado(id);
        }).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(rabbit);
    }
}
