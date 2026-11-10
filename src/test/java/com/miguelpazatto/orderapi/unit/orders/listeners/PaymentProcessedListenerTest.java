package com.miguelpazatto.orderapi.unit.orders.listeners;

import com.miguelpazatto.orderapi.orders.listeners.PaymentProcessedListener;
import com.miguelpazatto.orderapi.orders.services.OrderService;
import com.miguelpazatto.orderapi.payments.dtos.PaymentProcessedEventDTO;
import com.miguelpazatto.orderapi.payments.entities.enums.PaymentStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentProcessedListenerTest {

    @Mock
    private OrderService service;
    @InjectMocks
    private PaymentProcessedListener listener;
    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void shouldApplyKnownPaymentStatus(PaymentStatus status) {
        UUID id = UUID.randomUUID();
        listener.escutarAtualizacaoStatus(new PaymentProcessedEventDTO(id, status.name(), "user@test.com"));
        verify(service).atualizarStatusPagamento(id, status);
    }
    @Test
    void shouldIgnoreUnknownPaymentStatus() {
        listener.escutarAtualizacaoStatus(new PaymentProcessedEventDTO(UUID.randomUUID(), "UNKNOWN", "user@test.com"));
        verifyNoInteractions(service);
    }
}
