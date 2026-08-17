package com.miguelpazatto.orderapi.delivery.services;

import com.miguelpazatto.orderapi.core.exceptions.ResourceNotFoundException;
import com.miguelpazatto.orderapi.delivery.entities.Delivery;
import com.miguelpazatto.orderapi.delivery.repositories.DeliveryRepository;
import com.miguelpazatto.orderapi.orders.entities.Order;
import com.miguelpazatto.orderapi.orders.repositories.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;

    @Transactional
    public void criarEntregaParaPedido(UUID orderId) {
        Delivery delivery = new Delivery(orderId);
        deliveryRepository.save(delivery);

        log.info("Registro de entrega criado com sucesso para o pedido {}.", orderId);
    }
}
