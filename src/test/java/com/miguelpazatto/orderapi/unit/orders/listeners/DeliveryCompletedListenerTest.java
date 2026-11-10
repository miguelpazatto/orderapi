package com.miguelpazatto.orderapi.unit.orders.listeners;

import com.miguelpazatto.orderapi.orders.listeners.DeliveryCompletedListener;
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
class DeliveryCompletedListenerTest {

    @Mock
    private OrderService service;
    @InjectMocks
    private DeliveryCompletedListener listener;
    @Test
    void shouldDeliverOrder() {
        UUID id = UUID.randomUUID();
        listener.onDeliveryCompleted(id.toString());
        verify(service).deliverOrder(id);
    }
    @Test
    void shouldIgnoreMalformedIdentifier() {
        listener.onDeliveryCompleted("invalid");
        verifyNoInteractions(service);
    }
    @Test
    void shouldPropagateBusinessFailureForRetry() {
        UUID id = UUID.randomUUID();
        doThrow(new IllegalStateException("offline")).when(service).deliverOrder(id);
        assertThatThrownBy(() -> listener.onDeliveryCompleted(id.toString())).isInstanceOf(IllegalStateException.class);
    }
}
