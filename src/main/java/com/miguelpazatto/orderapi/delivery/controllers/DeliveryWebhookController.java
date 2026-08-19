package com.miguelpazatto.orderapi.delivery.controllers;

import com.miguelpazatto.orderapi.delivery.dtos.DeliveryUpdatePayloadDTO;
import com.miguelpazatto.orderapi.delivery.services.DeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/delivery")
@RequiredArgsConstructor
public class DeliveryWebhookController {

    private final DeliveryService deliveryService;

    @PostMapping
    public ResponseEntity<Void> receiveDeliveryUpdate(@RequestBody @Valid DeliveryUpdatePayloadDTO dto) {

        deliveryService.processWebhookUpdate(dto);

        return ResponseEntity.ok().build();
    }

}
