package com.miguelpazatto.orderapi.services;

import com.miguelpazatto.orderapi.config.RabbitMQConfig;
import com.miguelpazatto.orderapi.dtos.OrderCreatedEventDTO;
import com.miguelpazatto.orderapi.dtos.OrderItemRequestDTO;
import com.miguelpazatto.orderapi.dtos.OrderRequestDTO;
import com.miguelpazatto.orderapi.dtos.OrderResponseDTO;
import com.miguelpazatto.orderapi.entities.Customer;
import com.miguelpazatto.orderapi.entities.Order;
import com.miguelpazatto.orderapi.entities.OrderItem;
import com.miguelpazatto.orderapi.entities.Product;
import com.miguelpazatto.orderapi.entities.enums.OrderStatus;
import com.miguelpazatto.orderapi.repositories.CustomerRepository;
import com.miguelpazatto.orderapi.repositories.OrderRepository;
import com.miguelpazatto.orderapi.repositories.ProductRepository;
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

    @Transactional(readOnly = true)
    public List<OrderResponseDTO> findAll() {
        return orderRepository.findAll().stream()
                .map(OrderResponseDTO::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponseDTO findById(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pedido com ID " + id + " não encontrado"));

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
                .orElseThrow(() -> new RuntimeException("Cliente com ID " + dto.customerId() + " não encontrado"));

        Order order = new Order();
        order.setCustomer(customer);

        BigDecimal totalPrice = BigDecimal.ZERO;

        for (Map.Entry<UUID, Integer> entry : groupedItems.entrySet()) {
            UUID productId = entry.getKey();
            Integer quantity = entry.getValue();

            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("Produto com ID " + productId + " não encontrado"));

            if (product.getAvailableStock() < quantity) {
                throw new RuntimeException("Não há estoque disponível pro produto " + product.getName());
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

        return new OrderResponseDTO(order);
    }

    @Transactional
    public OrderResponseDTO cancelOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pedido com ID " + id + " não encontrado"));

        if (order.getOrderStatus() == OrderStatus.DELIVERED ||
                order.getOrderStatus() == OrderStatus.SHIPPED ||
                order.getOrderStatus() == OrderStatus.CANCELED) {
            throw new RuntimeException("Pedido já enviado, entregue ou cancelado");
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
                .orElseThrow(() -> new RuntimeException("Pedido com ID " + id + " não encontrado"));

        if (order.getOrderStatus() != OrderStatus.PAID) {
            throw new RuntimeException("Apenas pedidos com status PAGO podem ser enviados.");
        }

        order.setOrderStatus(OrderStatus.SHIPPED);

        order = orderRepository.save(order);
        return new OrderResponseDTO(order);
    }

    @Transactional
    public OrderResponseDTO deliverOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pedido com ID " + id + " não encontrado"));

        if (order.getOrderStatus() != OrderStatus.SHIPPED) {
            throw new RuntimeException("O pedido precisa ser ENVIADO antes de ser marcado como entregue.");
        }

        order.setOrderStatus(OrderStatus.DELIVERED);

        order = orderRepository.save(order);
        return new OrderResponseDTO(order);
    }

}
