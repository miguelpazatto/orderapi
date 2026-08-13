package com.miguelpazatto.orderapi.listeners;

import com.miguelpazatto.orderapi.config.RabbitMQConfig;
import com.miguelpazatto.orderapi.dtos.PaymentProcessedEventDTO;
import com.miguelpazatto.orderapi.entities.Order;
import com.miguelpazatto.orderapi.entities.enums.PaymentStatus;
import com.miguelpazatto.orderapi.repositories.OrderRepository;
import com.miguelpazatto.orderapi.services.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificacaoListener {

    private final EmailService emailService;
    private final OrderRepository orderRepository;

    @RabbitListener(queues = RabbitMQConfig.FILA_NOTIFICACAO)
    public void processarEnvioEmail(PaymentProcessedEventDTO evento) {
        log.info("[RABBITMQ] Recebendo evento de notificação para o Pedido: {}", evento.orderId());

        Order order = orderRepository.findById(evento.orderId())
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado na base para notificação."));

        String emailCliente = order.getCustomer().getEmail();

        emailService.enviarEmailAtualizacaoPagamento(order.getId(), evento.paymentStatus(), emailCliente);
    }

}
