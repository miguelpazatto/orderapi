package com.miguelpazatto.orderapi.orders.listeners;


import com.miguelpazatto.orderapi.core.config.RabbitMQConfig;
import com.miguelpazatto.orderapi.orders.services.OrderService;
import com.miguelpazatto.orderapi.payments.dtos.PaymentProcessedEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProcessedListener {

    private final OrderService orderService;

    @RabbitListener(queues = RabbitMQConfig.FILA_STATUS_PAGAMENTO)
    public void escutarAtualizacaoStatus(PaymentProcessedEventDTO evento) {
        log.info("[RABBITMQ] Recebendo atualização de status de pagamento para o Pedido: {}", evento.orderId());

        orderService.atualizarStatusPagamento(evento.orderId(), evento.paymentStatus());
    }

}
