package com.miguelpazatto.orderapi.delivery.services;

import com.miguelpazatto.orderapi.core.exceptions.ResourceNotFoundException;
import com.miguelpazatto.orderapi.delivery.clients.DeliveryClient;
import com.miguelpazatto.orderapi.delivery.dtos.DeliveryRequestDTO;
import com.miguelpazatto.orderapi.delivery.dtos.DeliveryResponseDTO;
import com.miguelpazatto.orderapi.delivery.entities.Delivery;
import com.miguelpazatto.orderapi.delivery.repositories.DeliveryRepository;
import com.miguelpazatto.orderapi.orders.entities.Order;
import com.miguelpazatto.orderapi.orders.repositories.OrderRepository;
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

        DeliveryRequestDTO request = new DeliveryRequestDTO(orderId);

        log.info("Solicitando rastreio para o pedido {}", orderId);
        DeliveryResponseDTO response = deliveryClient.solicitarRastreio(request);

        if (response == null || response.trackingCode() == null || response.trackingCode().isBlank()) {
            throw new ExternalIntegrationException("A transportadora não retornou um código de rastreio válido para o pedido: " + orderId);
        }

        delivery.assignTrackingCode(response.trackingCode());
        deliveryRepository.save(delivery);

        log.info("Entrega criada com sucesso. Pedido: {} | Rastreio: {}", orderId, delivery.getTrackingCode());
    }
}
