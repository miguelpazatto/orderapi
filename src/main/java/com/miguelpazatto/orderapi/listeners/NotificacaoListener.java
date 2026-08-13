package com.miguelpazatto.orderapi.listeners;

import com.miguelpazatto.orderapi.config.RabbitMQConfig;
import com.miguelpazatto.orderapi.dtos.CustomerResponseDTO;
import com.miguelpazatto.orderapi.dtos.OrderResponseDTO;
import com.miguelpazatto.orderapi.dtos.PaymentProcessedEventDTO;
import com.miguelpazatto.orderapi.entities.Customer;
import com.miguelpazatto.orderapi.entities.Order;
import com.miguelpazatto.orderapi.services.CustomerService;
import com.miguelpazatto.orderapi.services.EmailService;
import com.miguelpazatto.orderapi.services.OrderService;
import com.miguelpazatto.orderapi.services.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificacaoListener {

    private final EmailService emailService;
    private final OrderService orderService;
    private final CustomerService customerService;

    @RabbitListener(queues = RabbitMQConfig.FILA_NOTIFICACAO)
    public void processarEnvioEmail(PaymentProcessedEventDTO evento) {
        log.info("[RABBITMQ] Recebendo evento de notificação para o Pedido: {}", evento.orderId());

        OrderResponseDTO order = orderService.findById(evento.orderId());

        UUID customerId = order.customerId();
        CustomerResponseDTO customer = customerService.findById(customerId);

        emailService.enviarEmailAtualizacaoPagamento(order.id(), evento.paymentStatus(), customer.email());
    }

}
