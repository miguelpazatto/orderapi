package com.miguelpazatto.orderapi.integration.delivery;

import com.miguelpazatto.orderapi.core.config.RabbitMQConfig;
import com.miguelpazatto.orderapi.delivery.clients.DeliveryClient;
import com.miguelpazatto.orderapi.delivery.dtos.DeliveryUpdatePayloadDTO;
import com.miguelpazatto.orderapi.delivery.entities.Delivery;
import com.miguelpazatto.orderapi.delivery.entities.enums.DeliveryStatus;
import com.miguelpazatto.orderapi.delivery.repositories.DeliveryRepository;
import com.miguelpazatto.orderapi.delivery.services.DeliveryService;
import com.miguelpazatto.orderapi.integration.infra.AbstractIntegrationTest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DeliveryIntegrationTest extends AbstractIntegrationTest {
    @Autowired
    private DeliveryRepository deliveries;
    @Autowired
    private DeliveryService service;
    @MockBean
    private DeliveryClient client;
    @MockBean
    private RabbitTemplate rabbit;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        clearInvocations(rabbit);
        deliveries.deleteAll();
        orderId = UUID.randomUUID();
        deliveries.save(new Delivery(orderId));
    }

    @ParameterizedTest
    @EnumSource(DeliveryStatus.class)
    void shouldPersistPublicWebhookAndOnlyPublishCompletionOnce(DeliveryStatus status) {
        DeliveryUpdatePayloadDTO request = new DeliveryUpdatePayloadDTO(orderId, "TRACK-1", status.name());
        for (int i = 0; i < 2; i++) {
            given().spec(requestSpecification).body(request)
            .when().post("/webhooks/delivery")
            .then().log().ifValidationFails().statusCode(200);
        }
        Delivery updated = deliveries.findByOrderId(orderId).orElseThrow();
        assertThat(updated.getTrackingCode()).isEqualTo("TRACK-1");
        assertThat(updated.getDeliveryStatus()).isEqualTo(status);
        if (status == DeliveryStatus.DELIVERED) {
            verify(rabbit).convertAndSend(RabbitMQConfig.EXCHANGE_LOGISTICA, RabbitMQConfig.ROTA_ENTREGA_CONCLUIDA, orderId.toString());
        } else verifyNoInteractions(rabbit);
    }

    @ParameterizedTest
    @MethodSource("invalidUpdates")
    void shouldRejectInvalidWebhook(String field, String value) {
        Map<String, Object> body = new HashMap<>(Map.of("orderId", orderId.toString(), "trackingCode", "TRACK", "status", "DISPATCHED"));
        body.put(field, value);
        given().spec(requestSpecification).body(body)
        .when().post("/webhooks/delivery")
        .then().log().ifValidationFails().statusCode(400);
        assertThat(deliveries.findByOrderId(orderId).orElseThrow().getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
        verifyNoInteractions(rabbit);
    }

    @Test
    void shouldRejectUnknownOrder() {
        given().spec(requestSpecification).body(new DeliveryUpdatePayloadDTO(UUID.randomUUID(), "TRACK", "DISPATCHED"))
        .when().post("/webhooks/delivery")
        .then().log().ifValidationFails().statusCode(404);
    }

    @Test
    void shouldPreserveTrackingAndRejectStatusRegressionAfterDelivery() {
        Delivery delivery = deliveries.findByOrderId(orderId).orElseThrow();
        delivery.assignTrackingCode("ORIGINAL"); delivery.updateStatus(DeliveryStatus.DELIVERED); deliveries.save(delivery);
        given().spec(requestSpecification).body(new DeliveryUpdatePayloadDTO(orderId, null, "IN_TRANSIT"))
        .when().post("/webhooks/delivery")
        .then().log().ifValidationFails().statusCode(400);
        assertThat(deliveries.findByOrderId(orderId).orElseThrow().getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void shouldAcceptMissingTrackingOnSubsequentUpdate() {
        Delivery delivery = deliveries.findByOrderId(orderId).orElseThrow();
        delivery.assignTrackingCode("ORIGINAL"); deliveries.save(delivery);
        given().spec(requestSpecification).body(new DeliveryUpdatePayloadDTO(orderId, null, "IN_TRANSIT"))
        .when().post("/webhooks/delivery")
        .then().log().ifValidationFails().statusCode(200);
        assertThat(deliveries.findByOrderId(orderId).orElseThrow().getTrackingCode()).isEqualTo("ORIGINAL");
    }

    @Test
    void shouldKeepSavedDeliveryWhenCarrierFails() {
        UUID newOrderId = UUID.randomUUID();
        when(client.solicitarRastreio(any())).thenThrow(new RuntimeException("offline"));
        service.createDeliveryForOrder(newOrderId);
        assertThat(deliveries.findByOrderId(newOrderId)).isPresent();
    }

    static Stream<Arguments> invalidUpdates() {
        return Stream.concat(Stream.of(Arguments.of("orderId", null), Arguments.of("orderId", "invalid"),
                        Arguments.of("trackingCode", null), Arguments.of("trackingCode", ""), Arguments.of("trackingCode", "   ")),
                Stream.of(null, "", "   ", "UNKNOWN").map(value -> Arguments.of("status", value)));
    }
}
