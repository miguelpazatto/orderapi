package com.miguelpazatto.orderapi.controllers;

import com.miguelpazatto.orderapi.dtos.OrderRequestDTO;
import com.miguelpazatto.orderapi.dtos.OrderResponseDTO;
import com.miguelpazatto.orderapi.services.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public ResponseEntity<List<OrderResponseDTO>> findAll() {
        return ResponseEntity.ok().body(orderService.findAll());
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<OrderResponseDTO> findById(@PathVariable UUID id) {
        return ResponseEntity.ok().body(orderService.findById(id));
    }

    @PostMapping
    public ResponseEntity<OrderResponseDTO> insert(@RequestBody OrderRequestDTO dto) {
        OrderResponseDTO newDto = orderService.insert(dto);
        URI uri = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(newDto.id()).toUri();
        return ResponseEntity.created(uri).body(newDto);
    }

    @PatchMapping(value = "/{id}/cancel")
    public ResponseEntity<OrderResponseDTO> cancelOrder(@PathVariable UUID id) {
        return ResponseEntity.ok().body(orderService.cancelOrder(id));
    }

    @PatchMapping(value = "/{id}/ship")
    public ResponseEntity<OrderResponseDTO> shipOrder(@PathVariable UUID id) {
        return ResponseEntity.ok().body(orderService.shipOrder(id));
    }

    @PatchMapping(value = "/{id}/deliver")
    public ResponseEntity<OrderResponseDTO> deliverOrder(@PathVariable UUID id) {
        return ResponseEntity.ok().body(orderService.deliverOrder(id));
    }
}