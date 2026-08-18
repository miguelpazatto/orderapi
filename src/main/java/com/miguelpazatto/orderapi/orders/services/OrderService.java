package com.miguelpazatto.orderapi.orders.services;

import com.miguelpazatto.orderapi.core.config.RabbitMQConfig;
import com.miguelpazatto.orderapi.core.exceptions.BusinessRuleException;
import com.miguelpazatto.orderapi.core.exceptions.ResourceNotFoundException;
import com.miguelpazatto.orderapi.core.services.EmailService;
import com.miguelpazatto.orderapi.customers.dtos.CustomerResponseDTO;
import com.miguelpazatto.orderapi.customers.entities.Customer;
import com.miguelpazatto.orderapi.customers.services.CustomerService;
import com.miguelpazatto.orderapi.orders.dtos.*;
import com.miguelpazatto.orderapi.orders.entities.Order;
import com.miguelpazatto.orderapi.orders.entities.OrderItem;
import com.miguelpazatto.orderapi.orders.entities.enums.OrderStatus;
import com.miguelpazatto.orderapi.orders.repositories.OrderRepository;
import com.miguelpazatto.orderapi.payments.entities.enums.PaymentStatus;
import com.miguelpazatto.orderapi.products.entities.Product;
import com.miguelpazatto.orderapi.products.services.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerService customerService;
    private final ProductService productService;

    private final RabbitTemplate rabbitTemplate;

    private final EmailService emailService;

    @Transactional(readOnly = true)
    public List<OrderResponseDTO> findAll() {
        return orderRepository.findAll().stream()
                .map(OrderResponseDTO::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponseDTO findById(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido com ID " + id + " não encontrado"));

        return new OrderResponseDTO(order);
    }

    @Transactional
    public OrderResponseDTO insert(OrderRequestDTO dto) {

        Map<UUID, Integer> groupedItems = dto.items().stream()
                .collect(Collectors.toMap(
                        OrderItemRequestDTO::productId,
                        OrderItemRequestDTO::quantity,
                        Integer::sum
                ));

        CustomerResponseDTO customer = customerService.findById(dto.customerId());

        List<Order.ItemData> itemsData = new ArrayList<>();

        for (Map.Entry<UUID, Integer> entry : groupedItems.entrySet()) {
            UUID productId = entry.getKey();
            Integer quantity = entry.getValue();

            Product product = productService.findEntityById(productId);

            if (product.getAvailableStock() < quantity) {
                throw new BusinessRuleException("Não há estoque disponível pro produto " + product.getName());
            }

            product.decreaseStock(quantity);
            productService.save(product);

            itemsData.add(new Order.ItemData(
                    productId,
                    product.getName(),
                    quantity,
                    product.getPrice()
            ));
        }

        Order order = new Order(
                customer.id(),
                customer.name(),
                customer.email(),
                itemsData
        );

        order = orderRepository.save(order);
        log.info("Pedido {} salvo no banco de dados com sucesso.", order.getId());

        OrderCreatedEventDTO evento = new OrderCreatedEventDTO(order);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_PEDIDOS,
                RabbitMQConfig.ROTA_PEDIDO_CRIADO,
                evento
        );
        log.info("Evento de criação do pedido {} enviado para a fila de pagamentos.", order.getId());

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_DLX,
                RabbitMQConfig.ROTA_ESPERA_AVISO,
                order.getId()
        );

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_DLX,
                RabbitMQConfig.ROTA_ESPERA,
                order.getId()
        );
        log.info("Cronômetro de aviso e expiração iniciados para o pedido {}.", order.getId());

        return new OrderResponseDTO(order);
    }

    @Transactional
    public OrderResponseDTO cancelOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido com ID " + id + " não encontrado"));

        order.cancel();

        for (OrderItem item : order.getOrderItemList()) {
            Product product = productService.findEntityById(item.getProductId());

            product.updateStock(product.getAvailableStock() + item.getQuantity());

            productService.save(product);
        }

        order = orderRepository.save(order);
        return new OrderResponseDTO(order);
    }

    @Transactional
    public OrderResponseDTO shipOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido com ID " + id + " não encontrado"));

        order.dispatch();

        emailService.enviarEmailPedidoEnviado(order.getId(), order.getCustomerEmail());

        OrderDispatchedEventDTO event = new OrderDispatchedEventDTO(order.getId());

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_PEDIDOS,
                RabbitMQConfig.FILA_PEDIDO_DESPACHADO,
                event
        );

        return new OrderResponseDTO(order);
    }

    @Transactional
    public OrderResponseDTO deliverOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido com ID " + id + " não encontrado"));

        order.deliver();

        order = orderRepository.save(order);

        emailService.enviarEmailConfirmacaoEntrega(order.getId(), order.getCustomerEmail());

        return new OrderResponseDTO(order);
    }

    @Transactional
    public void atualizarStatusPagamento(UUID orderId, PaymentStatus paymentStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido com ID " + orderId + " não encontrado"));

        if (paymentStatus == PaymentStatus.APPROVED) {
            order.markAsPaid();
            log.info("Pedido {} atualizado para PAID!", orderId);

        } else if (paymentStatus == PaymentStatus.REJECTED) {
            order.markAsPaymentFailed();
            log.info("Pedido {} com falha no pagamento. Aguardando nova tentativa.", orderId);
        }

        orderRepository.save(order);
    }

    @Transactional
    public void cancelarPedidoExpirado(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido com ID " + orderId + " não encontrado"));

        if (order.getOrderStatus() == OrderStatus.WAITING_PAYMENT ||
                order.getOrderStatus() == OrderStatus.PAYMENT_FAILED) {

            log.warn("Tempo esgotado (TTL)! Chamando método padrão para cancelar pedido {}...", orderId);

            this.cancelOrder(orderId);
            emailService.enviarEmailCancelamentoPorInatividade(orderId, order.getCustomerEmail());

        } else {
            log.info("Pedido {} expirou na fila, mas já estava com status {}. Nenhuma ação necessária.",
                    orderId, order.getOrderStatus());
        }
    }

    public void alertarPedidoQuaseExpirado(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido com ID " + orderId + " não encontrado"));

        if (order.getOrderStatus() == OrderStatus.WAITING_PAYMENT ||
                order.getOrderStatus() == OrderStatus.PAYMENT_FAILED) {

            log.warn("Tempo de aviso esgotado (5s)! Enviando alerta para o cliente do pedido {}...", orderId);

            emailService.enviarEmailAvisoExpiracao(orderId, order.getCustomerEmail());

        } else {
            log.info("Aviso ignorado: O cliente já pagou ou o pedido {} já não está pendente.", orderId);
        }
    }
}
