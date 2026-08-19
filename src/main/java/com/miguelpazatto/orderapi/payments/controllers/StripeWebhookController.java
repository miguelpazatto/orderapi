package com.miguelpazatto.orderapi.payments.controllers;

import com.miguelpazatto.orderapi.core.exceptions.ExternalIntegrationException;
import com.miguelpazatto.orderapi.payments.services.StripeWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/webhooks/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final StripeWebhookService stripeWebhookService;

    @PostMapping
    public ResponseEntity<String> handleStripeEvent(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        try {
            stripeWebhookService.processStripeEvent(payload, sigHeader);
            return ResponseEntity.ok("Sucesso");

        } catch (ExternalIntegrationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}