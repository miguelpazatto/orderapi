package com.miguelpazatto.orderapi.services;

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
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

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
        return new OrderResponseDTO(order);
    }

}
