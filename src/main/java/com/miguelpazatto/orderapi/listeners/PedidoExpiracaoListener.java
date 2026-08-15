package com.miguelpazatto.orderapi.listeners;

import com.miguelpazatto.orderapi.config.RabbitMQConfig;
import com.miguelpazatto.orderapi.services.OrderService;
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
}
