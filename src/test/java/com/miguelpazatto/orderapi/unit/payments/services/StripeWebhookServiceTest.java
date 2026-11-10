package com.miguelpazatto.orderapi.unit.payments.services;

import com.miguelpazatto.orderapi.core.config.RabbitMQConfig;
import com.miguelpazatto.orderapi.core.exceptions.ExternalIntegrationException;
import com.miguelpazatto.orderapi.payments.services.StripeWebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {
    @Mock
    private RabbitTemplate rabbit;
    @InjectMocks
    private StripeWebhookService service;

    @BeforeEach
    void setUp() { ReflectionTestUtils.setField(service, "endpointSecret", "whsec_test"); }

    @ParameterizedTest
    @ValueSource(strings = {"signature", "payload"})
    void shouldRejectUntrustedOrMalformedEvent(String scenario) throws Exception {
        try (MockedStatic<Webhook> webhook = mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent("payload", "signature", "whsec_test"))
                    .thenThrow(scenario.equals("signature") ? new SignatureVerificationException("invalid", "signature") : new IllegalArgumentException("invalid"));
            assertThatThrownBy(() -> service.processStripeEvent("payload", "signature"))
                    .isInstanceOf(ExternalIntegrationException.class);
            verifyNoInteractions(rabbit);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "failure", "ignored", "other", "missing", "metadata", "null-metadata"})
    void shouldRouteOnlySupportedDeserializedPaymentEvents(String scenario) throws Exception {
        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        String type = scenario.equals("success") || scenario.equals("metadata") || scenario.equals("null-metadata") ? "payment_intent.succeeded" :
                scenario.equals("failure") ? "payment_intent.payment_failed" : "payment_intent.created";
        String paymentId = UUID.randomUUID().toString();
        PaymentIntent intent = new PaymentIntent();
        intent.setMetadata(scenario.equals("null-metadata") ? null : scenario.equals("metadata") ? Map.of() : Map.of("payment_id", paymentId));
        when(deserializer.getObject()).thenReturn(scenario.equals("missing") ? Optional.empty() :
                Optional.of(scenario.equals("other") ? new com.stripe.model.Customer() : intent));
        if (!scenario.equals("other")) when(event.getType()).thenReturn(type);
        try (MockedStatic<Webhook> webhook = mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent("payload", "signature", "whsec_test")).thenReturn(event);
            if (scenario.equals("missing") || scenario.equals("metadata") || scenario.equals("null-metadata")) {
                assertThatThrownBy(() -> service.processStripeEvent("payload", "signature"))
                        .isInstanceOf(ExternalIntegrationException.class);
            } else {
                service.processStripeEvent("payload", "signature");
            }
            if (scenario.equals("success") || scenario.equals("failure")) {
                verify(rabbit).convertAndSend(RabbitMQConfig.EXCHANGE_WEBHOOK,
                        scenario.equals("success") ? RabbitMQConfig.ROTA_WEBHOOK_SUCESSO : RabbitMQConfig.ROTA_WEBHOOK_FALHA, paymentId);
            } else verifyNoInteractions(rabbit);
        }
    }
}
