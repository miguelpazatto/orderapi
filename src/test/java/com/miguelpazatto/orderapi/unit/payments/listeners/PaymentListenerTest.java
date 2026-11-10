package com.miguelpazatto.orderapi.unit.payments.listeners;

import com.miguelpazatto.orderapi.orders.dtos.OrderCreatedEventDTO;
import com.miguelpazatto.orderapi.payments.listeners.PaymentListener;
import com.miguelpazatto.orderapi.payments.services.PaymentService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentListenerTest {

    @Mock
    private PaymentService service;
    @InjectMocks
    private PaymentListener listener;
    @Test
    void shouldProcessCreatedOrderAndPropagateFailure() {
        UUID id = UUID.randomUUID();
        OrderCreatedEventDTO event = new OrderCreatedEventDTO(id, UUID.randomUUID(), BigDecimal.TEN);
        listener.escutarFilaPagamentos(event);
        verify(service).processarPagamento(id, BigDecimal.TEN);
        doThrow(new IllegalStateException("offline")).when(service).processarPagamento(id, BigDecimal.TEN);
        assertThatThrownBy(() -> listener.escutarFilaPagamentos(event)).isInstanceOf(IllegalStateException.class);
    }
}
