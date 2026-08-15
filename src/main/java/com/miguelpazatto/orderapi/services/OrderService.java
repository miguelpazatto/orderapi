package com.miguelpazatto.orderapi.services;

import com.miguelpazatto.orderapi.config.RabbitMQConfig;
import com.miguelpazatto.orderapi.dtos.*;
import com.miguelpazatto.orderapi.entities.Customer;
import com.miguelpazatto.orderapi.entities.Order;
import com.miguelpazatto.orderapi.entities.OrderItem;
import com.miguelpazatto.orderapi.entities.Product;
import com.miguelpazatto.orderapi.entities.enums.OrderStatus;
import com.miguelpazatto.orderapi.entities.enums.PaymentStatus;
import com.miguelpazatto.orderapi.repositories.CustomerRepository;
import com.miguelpazatto.orderapi.repositories.OrderRepository;
import com.miguelpazatto.orderapi.repositories.ProductRepository;
import com.miguelpazatto.orderapi.services.exceptions.BusinessRuleException;
import com.miguelpazatto.orderapi.services.exceptions.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

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

        Customer customer = customerRepository.findById(dto.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente com ID " + dto.customerId() + " não encontrado"));

        Order order = new Order();
        order.setCustomer(customer);

        BigDecimal totalPrice = BigDecimal.ZERO;

        for (Map.Entry<UUID, Integer> entry : groupedItems.entrySet()) {
            UUID productId = entry.getKey();
            Integer quantity = entry.getValue();

            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Produto com ID " + productId + " não encontrado"));

            if (product.getAvailableStock() < quantity) {
                throw new BusinessRuleException("Não há estoque disponível pro produto " + product.getName());
            }

            product.setAvailableStock(product.getAvailableStock() - quantity);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(quantity);
            orderItem.setPrice(product.getPrice());

            order.getOrderItemList().add(orderItem);

            BigDecimal subTotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));
            totalPrice = totalPrice.add(subTotal);
        }

        order.setTotalPrice(totalPrice);
        order.setOrderStatus(OrderStatus.WAITING_PAYMENT);
        order.setPurchaseMoment(Instant.now());

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
                RabbitMQConfig.ROTA_ESPERA,
                order.getId()
        );
        log.info("Cronômetro de expiração iniciado para o pedido {}.", order.getId());

        return new OrderResponseDTO(order);
    }

    @Transactional
    public OrderResponseDTO cancelOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido com ID " + id + " não encontrado"));

        if (order.getOrderStatus() == OrderStatus.DELIVERED ||
                order.getOrderStatus() == OrderStatus.SHIPPED ||
                order.getOrderStatus() == OrderStatus.CANCELED) {
            throw new BusinessRuleException("Pedido já enviado, entregue ou cancelado");
        }

        for (OrderItem item : order.getOrderItemList()) {
            Product product = item.getProduct();
            product.setAvailableStock(product.getAvailableStock() + item.getQuantity());
        }

        order.setOrderStatus(OrderStatus.CANCELED);

        order = orderRepository.save(order);
        return new OrderResponseDTO(order);
    }

    @Transactional
    public OrderResponseDTO shipOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido com ID " + id + " não encontrado"));

        if (order.getOrderStatus() != OrderStatus.PAID) {
            throw new BusinessRuleException("Apenas pedidos com status PAGO podem ser enviados.");
        }

        order.setOrderStatus(OrderStatus.SHIPPED);

        order = orderRepository.save(order);
        emailService.enviarEmailPedidoEnviado(order.getId(), order.getCustomer().getEmail());

        return new OrderResponseDTO(order);
    }

    @Transactional
    public OrderResponseDTO deliverOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido com ID " + id + " não encontrado"));

        if (order.getOrderStatus() != OrderStatus.SHIPPED) {
            throw new BusinessRuleException("O pedido precisa ser ENVIADO antes de ser marcado como entregue.");
        }

        order.setOrderStatus(OrderStatus.DELIVERED);

        order = orderRepository.save(order);
        emailService.enviarEmailConfirmacaoEntrega(order.getId(), order.getCustomer().getEmail());

        return new OrderResponseDTO(order);
    }

    @Transactional
    public void atualizarStatusPagamento(UUID orderId, PaymentStatus paymentStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido com ID " + orderId + " não encontrado"));

        if (paymentStatus == PaymentStatus.APPROVED) {
            order.setOrderStatus(OrderStatus.PAID);
            log.info("Pedido {} atualizado para PAID!", orderId);

        } else if (paymentStatus == PaymentStatus.REJECTED) {
            order.setOrderStatus(OrderStatus.PAYMENT_FAILED);
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

            log.warn("Tempo esgotado (TTL)! Cancelando pedido {} e devolvendo estoque...", orderId);

            this.cancelOrder(orderId);
        } else {
            log.info("Pedido {} expirou na fila, mas já estava com status {}. Nenhuma ação necessária.",
                    orderId, order.getOrderStatus());
        }
    }
}
