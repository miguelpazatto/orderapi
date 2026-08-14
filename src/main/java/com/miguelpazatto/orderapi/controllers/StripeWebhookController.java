package com.miguelpazatto.orderapi.controllers;

import com.miguelpazatto.orderapi.config.RabbitMQConfig;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/webhooks/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    @Value("${stripe.webhook.secret}")
    private String endpointSecret;

    private final RabbitTemplate rabbitTemplate;

    @PostMapping
    public ResponseEntity<String> handleStripeEvent(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) throws EventDataObjectDeserializationException {

        Event event;

        try {
            event = Event.GSON.fromJson(payload, Event.class);
        //  event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        // } catch (SignatureVerificationException e) {
        //    log.error("Tentativa de fraude bloqueada! Assinatura inválida no Webhook do Stripe.", e);
        //    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Assinatura inválida");
        } catch (Exception e) {
            log.error("Erro inesperado ao processar payload do Stripe", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Erro interno");
        }

        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();

        switch (event.getType()) {
            case "payment_intent.succeeded": {
                PaymentIntent paymentIntent = (PaymentIntent) deserializer.deserializeUnsafe();

                String paymentId = paymentIntent.getMetadata().get("payment_id");

                log.info("Stripe confirmou o pagamento! Jogando ID {} para a fila de sucesso...", paymentId);

                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.EXCHANGE_WEBHOOK,
                        RabbitMQConfig.ROTA_WEBHOOK_SUCESSO,
                        paymentId
                );
            }
                break;
            case "payment_intent.payment_failed": {
                PaymentIntent paymentIntent = (PaymentIntent) deserializer.deserializeUnsafe();

                String paymentId = paymentIntent.getMetadata().get("payment_id");

                log.warn("Stripe recusou o pagamento. Jogando ID {} para a fila de falha...", paymentId);

                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.EXCHANGE_WEBHOOK,
                        RabbitMQConfig.ROTA_WEBHOOK_FALHA,
                        paymentId
                );
                break;
            }
            default:
                log.info("ℹEvento do Stripe ignorado: {}", event.getType());
                break;
        }

        return ResponseEntity.ok("Sucesso");

    }

}
