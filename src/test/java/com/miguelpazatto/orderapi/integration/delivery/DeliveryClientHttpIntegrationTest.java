package com.miguelpazatto.orderapi.integration.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miguelpazatto.orderapi.delivery.clients.DeliveryClient;
import com.miguelpazatto.orderapi.delivery.dtos.DeliveryRequestDTO;
import com.miguelpazatto.orderapi.delivery.repositories.DeliveryRepository;
import com.miguelpazatto.orderapi.delivery.services.DeliveryService;
import com.miguelpazatto.orderapi.integration.infra.AbstractIntegrationTest;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.*;

class DeliveryClientHttpIntegrationTest extends AbstractIntegrationTest {
    private static HttpServer carrier;
    private static final AtomicInteger responseStatus = new AtomicInteger(200);
    private static final AtomicReference<String> received = new AtomicReference<>();
    @Autowired
    private DeliveryClient client;
    @Autowired
    private DeliveryService service;
    @Autowired
    private DeliveryRepository deliveries;
    @Autowired
    private ObjectMapper mapper;

    @DynamicPropertySource
    static void carrierProperties(DynamicPropertyRegistry registry) throws Exception {
        carrier = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        carrier.createContext("/envios", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"trackingCode\":\"TRACK-HTTP\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus.get(), body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        carrier.start();
        registry.add("delivery.api.url", () -> "http://127.0.0.1:" + carrier.getAddress().getPort());
    }

    @AfterAll
    static void stopCarrier() { if (carrier != null) carrier.stop(0); }

    @ParameterizedTest
    @ValueSource(ints = {200, 503})
    void shouldSendRealHttpAndPreserveDeliveryWhenCarrierFails(int status) throws Exception {
        responseStatus.set(status);
        UUID orderId = UUID.randomUUID();
        service.createDeliveryForOrder(orderId);
        assertThat(deliveries.findByOrderId(orderId)).isPresent();
        assertThat(mapper.readTree(received.get()).get("orderId").asText()).isEqualTo(orderId.toString());
    }

    @Test
    void shouldDeserializeCarrierResponse() {
        responseStatus.set(200);
        assertThat(client.solicitarRastreio(new DeliveryRequestDTO(UUID.randomUUID())).trackingCode())
                .isEqualTo("TRACK-HTTP");
    }
}
