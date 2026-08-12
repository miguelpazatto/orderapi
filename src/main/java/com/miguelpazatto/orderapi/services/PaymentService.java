package com.miguelpazatto.orderapi.services;

import com.miguelpazatto.orderapi.config.RabbitMQConfig;
import com.miguelpazatto.orderapi.dtos.OrderCreatedEventDTO;
import com.miguelpazatto.orderapi.dtos.PaymentProcessedEventDTO;
import com.miguelpazatto.orderapi.entities.Payment;
import com.miguelpazatto.orderapi.entities.enums.PaymentStatus;
import com.miguelpazatto.orderapi.repositories.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    private final RabbitTemplate rabbitTemplate;

    public void processarPagamento(UUID orderId, BigDecimal totalPrice) {

        Payment payment = new Payment(orderId, totalPrice);
        paymentRepository.save(payment);

        log.info("Pagamento {} instanciado e salvo como PENDENTE para o Pedido {}", payment.getId(), orderId);

        try {
            Thread.sleep(2000);

            payment.setPaymentStatus(PaymentStatus.APPROVED);
            log.info("Gateway aprovou o pagamento {}!", payment.getId());
        } catch (InterruptedException e) {
            payment.setPaymentStatus(PaymentStatus.REJECTED);
            log.error("Erro na operadora. Pagamento {} recusado.", payment.getId(), e);
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
