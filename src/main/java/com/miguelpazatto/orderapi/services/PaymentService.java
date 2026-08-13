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
    }

    public void processarPagamentoAprovado(UUID paymentId) {
        log.info("Processando aprovação para o pagamento ID: {}", paymentId);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Pagamento não encontrado com o ID: " + paymentId));

        payment.setPaymentStatus(PaymentStatus.APPROVED);
        paymentRepository.save(payment);

        log.info("Pagamento {} atualizado para APPROVED no banco.", paymentId);

        PaymentProcessedEventDTO event = new PaymentProcessedEventDTO(payment);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_PAGAMENTO_CONCLUIDO,
                "",
                event
        );

        log.info("Mudança de status e notificação de pagamento concluído disparada para a Exchange Fanout!");
    }

    public void processarPagamentoRecusado(UUID paymentId) {
        log.info("Processando recusa para o pagamento ID: {}", paymentId);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Pagamento não encontrado com o ID: " + paymentId));

        payment.setPaymentStatus(PaymentStatus.REJECTED);
        paymentRepository.save(payment);

        log.warn("Pagamento {} atualizado para REJECTED no banco de dados.", paymentId);
        // TODO: Implementar no futuro o fluxo de cancelamento do pedido:

        PaymentProcessedEventDTO event = new PaymentProcessedEventDTO(payment);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_PAGAMENTO_CONCLUIDO,
                "",
                event
        );

        log.info("Mudança de status e notificação de pagamento concluído disparada para a Exchange Fanout!");
    }

}
