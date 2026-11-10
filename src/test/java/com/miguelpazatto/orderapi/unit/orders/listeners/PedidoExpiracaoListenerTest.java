package com.miguelpazatto.orderapi.unit.orders.listeners;

import com.miguelpazatto.orderapi.orders.listeners.PedidoExpiracaoListener;
import com.miguelpazatto.orderapi.orders.services.OrderService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoExpiracaoListenerTest {

    @Mock
    private OrderService service;
    @InjectMocks
    private PedidoExpiracaoListener listener;
    @Test
    void shouldDelegateExpirationAndWarning() {
        UUID id = UUID.randomUUID();
        listener.processarExpiracao(id);
        listener.processarAviso(id);
        verify(service).cancelarPedidoExpirado(id);
        verify(service).alertarPedidoQuaseExpirado(id);
    }
}
