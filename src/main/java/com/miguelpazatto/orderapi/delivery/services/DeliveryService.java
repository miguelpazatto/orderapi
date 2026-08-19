package com.miguelpazatto.orderapi.delivery.services;

import com.miguelpazatto.orderapi.core.exceptions.ExternalIntegrationException;
import com.miguelpazatto.orderapi.core.exceptions.ResourceNotFoundException;
import com.miguelpazatto.orderapi.delivery.clients.DeliveryClient;
import com.miguelpazatto.orderapi.delivery.dtos.DeliveryRequestDTO;
import com.miguelpazatto.orderapi.delivery.dtos.DeliveryResponseDTO;
import com.miguelpazatto.orderapi.delivery.dtos.DeliveryUpdatePayloadDTO;
import com.miguelpazatto.orderapi.delivery.entities.Delivery;
import com.miguelpazatto.orderapi.delivery.repositories.DeliveryRepository;
import com.miguelpazatto.orderapi.orders.entities.Order;
import com.miguelpazatto.orderapi.orders.repositories.OrderRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;

    private final DeliveryClient deliveryClient;

    @Transactional
    public void createDeliveryForOrder(UUID orderId) {
        Delivery delivery = new Delivery(orderId);
        deliveryRepository.save(delivery);

        log.info("Entrega salva no banco. Avisando transportadora para o pedido: {}", orderId);

        DeliveryRequestDTO request = new DeliveryRequestDTO(orderId);

        try {
            deliveryClient.solicitarRastreio(request);
        } catch (Exception e) {
            log.error("A transportadora falhou em receber a notificação, mas a entrega está salva. Pedido: {}", orderId);
        }
    }

    public void processWebhookUpdate(@Valid DeliveryUpdatePayloadDTO dto) {



    }
}
