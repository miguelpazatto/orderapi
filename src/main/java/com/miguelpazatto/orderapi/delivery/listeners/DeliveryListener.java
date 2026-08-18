package com.miguelpazatto.orderapi.delivery.listeners;

import com.miguelpazatto.orderapi.core.config.RabbitMQConfig;
import com.miguelpazatto.orderapi.delivery.services.DeliveryService;
import com.miguelpazatto.orderapi.orders.dtos.OrderDispatchedEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryListener {

    private final DeliveryService deliveryService;

    @RabbitListener(queues = RabbitMQConfig.FILA_PEDIDO_DESPACHADO)
    public void consumirPedidoDespachado(OrderDispatchedEventDTO event) {
        log.info("Recebido evento de pedido despachado para o ID: {}", event.orderId());

        deliveryService.createDeliveryForOrder(event.orderId());
    }
}
