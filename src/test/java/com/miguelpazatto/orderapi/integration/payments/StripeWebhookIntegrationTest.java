package com.miguelpazatto.orderapi.integration.payments;

import com.miguelpazatto.orderapi.core.config.RabbitMQConfig;
import com.miguelpazatto.orderapi.integration.infra.AbstractIntegrationTest;
import com.miguelpazatto.orderapi.integration.infra.StripeWebhookFixtures;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.mock.mockito.MockBean;

import static io.restassured.RestAssured.given;
import static org.mockito.Mockito.*;

class StripeWebhookIntegrationTest extends AbstractIntegrationTest {
    @MockBean
    private RabbitTemplate rabbit;

    @ParameterizedTest
    @ValueSource(strings = {"payment_intent.succeeded", "payment_intent.payment_failed"})
    void shouldVerifyRealSignatureAndRoutePaymentResult(String type) {
        String id = UUID.randomUUID().toString();
        String payload = StripeWebhookFixtures.payload(type, "payment_intent", "{\"payment_id\":\"" + id + "\"}");
        given().spec(requestSpecification).header("Stripe-Signature", StripeWebhookFixtures.signature(payload)).body(payload)
        .when().post("/webhooks/stripe")
        .then().log().ifValidationFails().statusCode(200);
        verify(rabbit).convertAndSend(RabbitMQConfig.EXCHANGE_WEBHOOK,
                type.endsWith("succeeded") ? RabbitMQConfig.ROTA_WEBHOOK_SUCESSO : RabbitMQConfig.ROTA_WEBHOOK_FALHA, id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-signature", "expired-signature", "tampered", "malformed", "missing-header", "metadata", "invalid-metadata", "api-version"})
    void shouldRejectUntrustedOrUnusableWebhook(String scenario) {
        String metadata = scenario.equals("metadata") ? "{}" : "{\"payment_id\":\"" +
                (scenario.equals("invalid-metadata") ? "invalid" : UUID.randomUUID()) + "\"}";
        String payload = StripeWebhookFixtures.payload("payment_intent.succeeded", "payment_intent", metadata);
        if (scenario.equals("api-version")) payload = payload.replace(com.stripe.Stripe.API_VERSION, "2010-01-01");
        if (scenario.equals("malformed")) payload = "{";
        String signature = scenario.equals("expired-signature") ? StripeWebhookFixtures.signature(payload, "whsec_integration_test",
                Instant.now().minusSeconds(600).getEpochSecond()) : StripeWebhookFixtures.signature(payload);
        if (scenario.equals("invalid-signature")) signature = "t=1,v1=invalid";
        if (scenario.equals("tampered")) payload = payload.replace("obj_test", "obj_changed");
        var request = given().spec(requestSpecification).body(payload);
        if (!scenario.equals("missing-header")) request.header("Stripe-Signature", signature);
        request.when().post("/webhooks/stripe").then().log().ifValidationFails().statusCode(400);
        verifyNoInteractions(rabbit);
    }

    @BeforeEach
    void clearStartupInteractions() { clearInvocations(rabbit); }

    @Test
    void shouldIgnoreUnsupportedEvents() {
        String payload = StripeWebhookFixtures.payload("customer.created", "customer", "{}");
        given().spec(requestSpecification).header("Stripe-Signature", StripeWebhookFixtures.signature(payload)).body(payload)
        .when().post("/webhooks/stripe")
        .then().log().ifValidationFails().statusCode(200);
        verifyNoInteractions(rabbit);
    }
}
