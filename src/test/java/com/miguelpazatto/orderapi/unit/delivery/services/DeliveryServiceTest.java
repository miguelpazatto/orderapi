package com.miguelpazatto.orderapi.unit.delivery.services;

import com.miguelpazatto.orderapi.core.config.RabbitMQConfig;
import com.miguelpazatto.orderapi.core.exceptions.ExternalIntegrationException;
import com.miguelpazatto.orderapi.core.exceptions.ResourceNotFoundException;
import com.miguelpazatto.orderapi.delivery.clients.DeliveryClient;
import com.miguelpazatto.orderapi.delivery.dtos.DeliveryRequestDTO;
import com.miguelpazatto.orderapi.delivery.dtos.DeliveryUpdatePayloadDTO;
import com.miguelpazatto.orderapi.delivery.entities.Delivery;
import com.miguelpazatto.orderapi.delivery.entities.enums.DeliveryStatus;
import com.miguelpazatto.orderapi.delivery.repositories.DeliveryRepository;
import com.miguelpazatto.orderapi.delivery.services.DeliveryService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {
    @Mock
    private DeliveryRepository deliveries;
    @Mock
    private DeliveryClient client;
    @Mock
    private RabbitTemplate rabbit;
    @InjectMocks
    private DeliveryService service;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldKeepDeliveryWhenCarrierFails(boolean fails) {
        UUID orderId = UUID.randomUUID();
        if (fails) when(client.solicitarRastreio(any())).thenThrow(new RuntimeException("offline"));
        assertThatCode(() -> service.createDeliveryForOrder(orderId)).doesNotThrowAnyException();
        verify(deliveries).save(argThat(delivery -> delivery.getOrderId().equals(orderId)));
        verify(client).solicitarRastreio(new DeliveryRequestDTO(orderId));
    }

    @ParameterizedTest
    @EnumSource(DeliveryStatus.class)
    void shouldUpdateDeliveryAndOnlyPublishCompletionOnce(DeliveryStatus status) {
        UUID orderId = UUID.randomUUID();
        Delivery delivery = new Delivery(orderId);
        when(deliveries.findByOrderId(orderId)).thenReturn(Optional.of(delivery));
        DeliveryUpdatePayloadDTO update = new DeliveryUpdatePayloadDTO(orderId, "TRACK-1", status.name());
        service.processWebhookUpdate(update);
        service.processWebhookUpdate(update);
        assertThat(delivery.getDeliveryStatus()).isEqualTo(status);
        assertThat(delivery.getTrackingCode()).isEqualTo("TRACK-1");
        if (status == DeliveryStatus.DELIVERED) {
            verify(rabbit).convertAndSend(RabbitMQConfig.EXCHANGE_LOGISTICA, RabbitMQConfig.ROTA_ENTREGA_CONCLUIDA, orderId.toString());
        } else verifyNoInteractions(rabbit);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void shouldRequireTrackingOnFirstUpdate(String tracking) {
        UUID id = UUID.randomUUID();
        when(deliveries.findByOrderId(id)).thenReturn(Optional.of(new Delivery(id)));
        assertThatThrownBy(() -> service.processWebhookUpdate(new DeliveryUpdatePayloadDTO(id, tracking, "DISPATCHED")))
                .isInstanceOf(ExternalIntegrationException.class);
        verify(deliveries, never()).save(any());
    }

    @Test
    void shouldAllowMissingTrackingAfterFirstUpdate() {
        UUID id = UUID.randomUUID();
        Delivery delivery = new Delivery(id);
        delivery.assignTrackingCode("TRACK-1");
        when(deliveries.findByOrderId(id)).thenReturn(Optional.of(delivery));
        service.processWebhookUpdate(new DeliveryUpdatePayloadDTO(id, null, "IN_TRANSIT"));
        assertThat(delivery.getTrackingCode()).isEqualTo("TRACK-1");
    }

    @Test
    void shouldRejectMissingDelivery() {
        assertThatThrownBy(() -> service.processWebhookUpdate(new DeliveryUpdatePayloadDTO(UUID.randomUUID(), "TRACK", "DISPATCHED")))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(rabbit);
    }
}
