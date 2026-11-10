package com.miguelpazatto.orderapi.unit.payments.listeners;

import com.miguelpazatto.orderapi.payments.listeners.StripeWebhookListener;
import com.miguelpazatto.orderapi.payments.services.PaymentService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeWebhookListenerTest {

    @Mock
    private PaymentService service;
    @InjectMocks
    private StripeWebhookListener listener;
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldDispatchPaymentResult(boolean approved) {
        UUID id = UUID.randomUUID();
        if (approved) {
            listener.ouvirPagamentoSucesso(id.toString());
            verify(service).processarPagamentoAprovado(id);
        } else {
            listener.ouvirPagamentoFalha(id.toString());
            verify(service).processarPagamentoRecusado(id);
        }
    }
    @Test
    void shouldRejectMalformedPaymentIdentifier() {
        assertThatThrownBy(() -> listener.ouvirPagamentoSucesso("invalid")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> listener.ouvirPagamentoFalha("invalid")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(service);
    }
}
