package com.miguelpazatto.orderapi.unit.delivery.listeners;

import com.miguelpazatto.orderapi.delivery.listeners.DeliveryListener;
import com.miguelpazatto.orderapi.delivery.services.DeliveryService;
import com.miguelpazatto.orderapi.orders.dtos.OrderDispatchedEventDTO;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryListenerTest {

    @Mock
    private DeliveryService service;
    @InjectMocks
    private DeliveryListener listener;
    @Test
    void shouldCreateDeliveryForDispatchedOrderAndPropagateFailure() {
        UUID id = UUID.randomUUID();
        listener.consumirPedidoDespachado(new OrderDispatchedEventDTO(id));
        verify(service).createDeliveryForOrder(id);
        doThrow(new IllegalStateException("offline")).when(service).createDeliveryForOrder(id);
        assertThatThrownBy(() -> listener.consumirPedidoDespachado(new OrderDispatchedEventDTO(id)))
                .isInstanceOf(IllegalStateException.class);
    }
}
