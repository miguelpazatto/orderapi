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
public class PedidoExpiracaoListener {

    private final OrderService orderService;

    @RabbitListener(queues = RabbitMQConfig.FILA_CANCELAMENTO_DLQ)
    public void processarExpiracao(UUID orderId) {
        log.info("DLQ ACIONADA: Mensagem recebida no cemitério para o pedido {}", orderId);

        orderService.cancelarPedidoExpirado(orderId);
    }

    @RabbitListener(queues = RabbitMQConfig.FILA_AVISO_DLQ)
    public void processarAviso(UUID orderId) {
        log.info("DLQ AVISO ACIONADA: Mensagem recebida no cemitério de alertas para o pedido {}", orderId);

        orderService.alertarPedidoQuaseExpirado(orderId);
    }
}
