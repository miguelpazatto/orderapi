package com.miguelpazatto.orderapi.listeners;

import com.miguelpazatto.orderapi.config.RabbitMQConfig;
import com.miguelpazatto.orderapi.services.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class StripeWebhookListener {

    private final PaymentService paymentService;

    @RabbitListener(queues = RabbitMQConfig.FILA_WEBHOOK_SUCESSO)
    public void ouvirPagamentoSucesso(String paymentIdString) {
        log.info("[RabbitMQ] Mensagem recebida na fila de SUCESSO. ID: {}", paymentIdString);
        UUID paymentId = UUID.fromString(paymentIdString);
        paymentService.processarPagamentoAprovado(paymentId);
    }

    @RabbitListener(queues = RabbitMQConfig.FILA_WEBHOOK_FALHA)
    public void ouvirPagamentoFalha(String paymentIdString) {
        log.warn("[RabbitMQ] Mensagem recebida na fila de FALHA. ID: {}", paymentIdString);
        UUID paymentId = UUID.fromString(paymentIdString);
        paymentService.processarPagamentoRecusado(paymentId);
    }
}
