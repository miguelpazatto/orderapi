package com.miguelpazatto.orderapi.unit.core.listeners;

import com.miguelpazatto.orderapi.core.listeners.NotificacaoListener;
import com.miguelpazatto.orderapi.core.services.EmailService;
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
class NotificacaoListenerTest {

    @Mock
    private EmailService service;
    @InjectMocks
    private NotificacaoListener listener;
    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void shouldNotifyPaymentResult(PaymentStatus status) {
        UUID id = UUID.randomUUID();
        listener.processarEnvioEmail(new PaymentProcessedEventDTO(id, status.name(), "user@test.com"));
        verify(service).enviarEmailAtualizacaoPagamento(id, status, "user@test.com");
    }
    @Test
    void shouldRejectUnknownPaymentStatus() {
        assertThatThrownBy(() -> listener.processarEnvioEmail(new PaymentProcessedEventDTO(UUID.randomUUID(), "UNKNOWN", "user@test.com")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(service);
    }
}
