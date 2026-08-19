package com.miguelpazatto.orderapi.orders.listeners;

import com.miguelpazatto.orderapi.core.config.RabbitMQConfig;
import com.miguelpazatto.orderapi.orders.services.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryCompletedListener {

    private final OrderService orderService;

    @RabbitListener(queues = RabbitMQConfig.FILA_ENTREGA_CONCLUIDA)
    public void onDeliveryCompleted(String orderIdString) {
        log.info("[RABBITMQ] Recebido aviso de entrega concluída para o Pedido ID: {}", orderIdString);

        try {
            UUID orderId = UUID.fromString(orderIdString);
            orderService.deliverOrder(orderId);

        } catch (IllegalArgumentException e) {
            log.error("[RABBITMQ] Erro de formatação: ID de pedido inválido recebido da fila de entrega: {}", orderIdString);
        } catch (Exception e) {
            log.error("[RABBITMQ] Erro ao processar a finalização do pedido {}", orderIdString, e);
            throw e;
        }

    }

}
