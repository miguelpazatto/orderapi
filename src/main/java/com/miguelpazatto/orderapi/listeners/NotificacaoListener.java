package com.miguelpazatto.orderapi.listeners;

import com.miguelpazatto.orderapi.config.RabbitMQConfig;
import com.miguelpazatto.orderapi.dtos.PaymentProcessedEventDTO;
import com.miguelpazatto.orderapi.entities.enums.PaymentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificacaoListener {

    @RabbitListener(queues = RabbitMQConfig.FILA_NOTIFICACAO)
    public void processarEnvioEmail(PaymentProcessedEventDTO evento) {
        log.info("=====================================================");
        log.info("📧 [NOTIFICAÇÃO] Preparando e-mail para o Pedido: {}", evento.orderId());

        if (evento.paymentStatus() == PaymentStatus.APPROVED) {
            log.info("E-MAIL DISPARADO: 'Olá! Seu pagamento foi aprovado e seu pedido já está sendo separado!'");
        } else if (evento.paymentStatus() == PaymentStatus.REJECTED) {
            log.info("E-MAIL DISPARADO: 'Ops! Tivemos um problema com seu pagamento. Por favor, revise os dados ou tente outro cartão.'");
        }

        log.info("=====================================================");
    }

}
