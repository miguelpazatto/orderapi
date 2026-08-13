package com.miguelpazatto.orderapi.services;

import com.miguelpazatto.orderapi.config.RabbitMQConfig;
import com.miguelpazatto.orderapi.dtos.OrderCreatedEventDTO;
import com.miguelpazatto.orderapi.dtos.PaymentProcessedEventDTO;
import com.miguelpazatto.orderapi.entities.Payment;
import com.miguelpazatto.orderapi.entities.enums.PaymentStatus;
import com.miguelpazatto.orderapi.repositories.PaymentRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    private final RabbitTemplate rabbitTemplate;

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    public void processarPagamento(UUID orderId, BigDecimal totalPrice) {

        Payment payment = new Payment(orderId, totalPrice);
        paymentRepository.save(payment);

        log.info("Pagamento {} instanciado e salvo como PENDENTE para o Pedido {}", payment.getId(), orderId);

            Stripe.apiKey = stripeApiKey;

            long totalPriceCents = totalPrice.multiply(new BigDecimal("100")).longValue();

            PaymentIntentCreateParams params = new PaymentIntentCreateParams.Builder()
                    .setAmount(totalPriceCents)
                    .setCurrency("brl")
                    .putMetadata("payment_id", payment.getId().toString())
                    .build();

        try {
            PaymentIntent intent = PaymentIntent.create(params);
            log.info("Cobrança gerada no Stripe com sucesso! ID Stripe: {}", intent.getId());
        } catch (StripeException e) {
            log.error("Erro ao comunicar com o Stripe na criação", e);
        }

        paymentRepository.save(payment);
        log.info("Status final do pagamento {}: atualizado no banco.", payment.getId());

        PaymentProcessedEventDTO eventoSaida = new PaymentProcessedEventDTO(payment);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_PAGAMENTO_CONCLUIDO,
                "",
                eventoSaida
        );

        log.info("Mensagem de pagamento concluído disparada para a Exchange Fanout!");

    }

}
