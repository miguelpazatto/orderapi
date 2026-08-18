package com.miguelpazatto.orderapi.payments.controllers;


import com.miguelpazatto.orderapi.core.config.RabbitMQConfig;
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
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;

        try {
          event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
         } catch (SignatureVerificationException e) {
            log.error("Tentativa de fraude bloqueada! Assinatura inválida no Webhook do Stripe.", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Assinatura inválida");
        } catch (Exception e) {
            log.error("Erro inesperado ao processar payload do Stripe", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Erro interno");
        }

        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();

        StripeObject stripeObject = deserializer.getObject().orElse(null);

        if (stripeObject == null) {
            log.error("Falha ao deserializar evento {}. Incompatibilidade de API? JSON bruto: {}",
                    event.getType(), deserializer.getRawJson());
            return ResponseEntity.badRequest().body("Falha na deserialização do payload");
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

        return ResponseEntity.ok("Sucesso");

    }

}
