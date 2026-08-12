package com.miguelpazatto.orderapi.listeners;

import com.miguelpazatto.orderapi.config.RabbitMQConfig;
import com.miguelpazatto.orderapi.dtos.OrderCreatedEventDTO;
import com.miguelpazatto.orderapi.services.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentListener {

    private final PaymentService paymentService;

    public void escutarFilaPagamentos(OrderCreatedEventDTO evento) {

        log.info("=====================================================");
        log.info("📬 [RABBITMQ] Novo evento consumido da fila: {}", RabbitMQConfig.FILA_PAGAMENTO);

        paymentService.processarPagamento(evento);

        log.info("=====================================================");
    }

}
