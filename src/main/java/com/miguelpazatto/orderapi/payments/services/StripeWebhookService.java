package com.miguelpazatto.orderapi.payments.services;

import com.miguelpazatto.orderapi.core.config.RabbitMQConfig;
import com.miguelpazatto.orderapi.core.exceptions.ExternalIntegrationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeWebhookService {

    @Value("${stripe.webhook.secret}")
    private String endpointSecret;

    private final RabbitTemplate rabbitTemplate;

    public void processStripeEvent(String payload, String sigHeader) {
        Event event;

        try {
            event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        } catch (SignatureVerificationException e) {
            log.error("Tentativa de fraude bloqueada! Assinatura inválida no Webhook do Stripe.", e);
            throw new ExternalIntegrationException("Assinatura inválida");
        } catch (Exception e) {
            log.error("Erro inesperado ao processar payload do Stripe", e);
            throw new ExternalIntegrationException("Erro interno ao processar Webhook");
        }

        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject = deserializer.getObject().orElse(null);

        if (stripeObject == null) {
            log.error("Falha ao deserializar evento {}. JSON bruto: {}", event.getType(), deserializer.getRawJson());
            throw new ExternalIntegrationException("Falha na deserialização do payload");
        }

        if (stripeObject instanceof PaymentIntent paymentIntent) {
            String paymentId = paymentIntent.getMetadata().get("payment_id");

            switch (event.getType()) {
                case "payment_intent.succeeded" -> {
                    log.info("Stripe confirmou o pagamento! Jogando ID {} para a fila de sucesso...", paymentId);
                    rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_WEBHOOK, RabbitMQConfig.ROTA_WEBHOOK_SUCESSO, paymentId);
                }
                case "payment_intent.payment_failed" -> {
                    log.warn("Stripe recusou o pagamento. Jogando ID {} para a fila de falha...", paymentId);
                    rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_WEBHOOK, RabbitMQConfig.ROTA_WEBHOOK_FALHA, paymentId);
                }
                default -> log.info("Evento de PaymentIntent não mapeado ignorado: {}", event.getType());
            }
        } else {
            log.info("Evento do Stripe ignorado (Não é do tipo PaymentIntent): {}", event.getType());
        }
    }
}