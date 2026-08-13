package com.miguelpazatto.orderapi.listeners;

import com.miguelpazatto.orderapi.config.RabbitMQConfig;
import com.miguelpazatto.orderapi.dtos.OrderCreatedEventDTO;
import com.miguelpazatto.orderapi.services.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentListener {

    private final PaymentService paymentService;

    @RabbitListener(queues = RabbitMQConfig.FILA_PAGAMENTO)
    public void escutarFilaPagamentos(OrderCreatedEventDTO evento) {
        log.info("[RABBITMQ] Novo evento consumido da fila: {}", RabbitMQConfig.FILA_PAGAMENTO);

        paymentService.processarPagamento(evento.orderId(), evento.totalPrice());
    }

}
