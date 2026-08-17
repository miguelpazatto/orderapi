package com.miguelpazatto.orderapi.core.listeners;

import com.miguelpazatto.orderapi.core.config.RabbitMQConfig;
import com.miguelpazatto.orderapi.core.services.EmailService;
import com.miguelpazatto.orderapi.payments.dtos.PaymentProcessedEventDTO;
import com.miguelpazatto.orderapi.payments.entities.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificacaoListener {

    private final EmailService emailService;

    @RabbitListener(queues = RabbitMQConfig.FILA_NOTIFICACAO)
    public void processarEnvioEmail(PaymentProcessedEventDTO evento) {
        log.info("[RABBITMQ] Recebendo evento de notificação para o Pedido: {}", evento.orderId());

        emailService.enviarEmailAtualizacaoPagamento(
                evento.orderId(),
                PaymentStatus.valueOf(evento.paymentStatus()),
                evento.customerEmail()
        );
    }

}
