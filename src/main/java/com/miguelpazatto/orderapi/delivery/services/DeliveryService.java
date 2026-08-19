package com.miguelpazatto.orderapi.delivery.services;

import com.miguelpazatto.orderapi.core.config.RabbitMQConfig;
import com.miguelpazatto.orderapi.core.exceptions.ExternalIntegrationException;
import com.miguelpazatto.orderapi.core.exceptions.ResourceNotFoundException;
import com.miguelpazatto.orderapi.delivery.clients.DeliveryClient;
import com.miguelpazatto.orderapi.delivery.dtos.DeliveryRequestDTO;
import com.miguelpazatto.orderapi.delivery.dtos.DeliveryResponseDTO;
import com.miguelpazatto.orderapi.delivery.dtos.DeliveryUpdatePayloadDTO;
import com.miguelpazatto.orderapi.delivery.entities.Delivery;
import com.miguelpazatto.orderapi.delivery.entities.enums.DeliveryStatus;
import com.miguelpazatto.orderapi.delivery.repositories.DeliveryRepository;
import com.miguelpazatto.orderapi.orders.entities.Order;
import com.miguelpazatto.orderapi.orders.repositories.OrderRepository;
import com.miguelpazatto.orderapi.orders.services.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;

    private final DeliveryClient deliveryClient;

    private final RabbitTemplate rabbitTemplate;

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

    @Transactional
    public void processWebhookUpdate(@Valid DeliveryUpdatePayloadDTO dto) {
        Delivery delivery = deliveryRepository.findByOrderId(dto.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Pedido de ID " + dto.orderId() + "não encontrado"));

        if (delivery.getTrackingCode() == null) {
            if (dto.trackingCode() == null) {
                throw new ExternalIntegrationException("O código de rastreio não pode ser nulo no primeiro envio");
            }
            delivery.assignTrackingCode(dto.trackingCode());
        }

        DeliveryStatus newStatus = DeliveryStatus.valueOf(dto.status());
        delivery.updateStatus(newStatus);

        deliveryRepository.save(delivery);

        if (newStatus == DeliveryStatus.DELIVERED) {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_LOGISTICA,
                    RabbitMQConfig.ROTA_ENTREGA_CONCLUIDA,
                    delivery.getOrderId().toString());
        }
    }
}
