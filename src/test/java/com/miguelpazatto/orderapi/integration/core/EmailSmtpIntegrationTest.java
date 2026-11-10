package com.miguelpazatto.orderapi.integration.core;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import com.miguelpazatto.orderapi.core.services.EmailService;
import com.miguelpazatto.orderapi.payments.entities.enums.PaymentStatus;
import jakarta.mail.Message;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class EmailSmtpIntegrationTest {
    private GreenMail smtp;
    private EmailService service;
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        smtp = new GreenMail(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));
        smtp.start();
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("127.0.0.1");
        sender.setPort(smtp.getSmtp().getPort());
        sender.setDefaultEncoding("UTF-8");
        service = new EmailService(sender);
        ReflectionTestUtils.setField(service, "remetente", "loja@test.com");
        ReflectionTestUtils.setField(service, "emailAdmin", "admin@test.com");
    }

    @AfterEach
    void stopSmtp() { if (smtp != null) smtp.stop(); }

    @ParameterizedTest
    @ValueSource(strings = {"shipping", "delivery", "warning", "expiration", "approved", "rejected"})
    void shouldDeliverMessagesThroughRealLocalSmtp(String operation) throws Exception {
        switch (operation) {
            case "shipping" -> service.enviarEmailPedidoEnviado(orderId, "cliente@test.com");
            case "delivery" -> service.enviarEmailConfirmacaoEntrega(orderId, "cliente@test.com");
            case "warning" -> service.enviarEmailAvisoExpiracao(orderId, "cliente@test.com");
            case "expiration" -> service.enviarEmailCancelamentoPorInatividade(orderId, "cliente@test.com");
            case "approved" -> service.enviarEmailAtualizacaoPagamento(orderId, PaymentStatus.APPROVED, "cliente@test.com");
            case "rejected" -> service.enviarEmailAtualizacaoPagamento(orderId, PaymentStatus.REJECTED, "cliente@test.com");
            default -> throw new IllegalArgumentException(operation);
        }
        int expected = operation.equals("delivery") || operation.equals("approved") ? 2 : 1;
        assertThat(smtp.waitForIncomingEmail(5000, expected)).isTrue();
        assertThat(smtp.getReceivedMessages()).hasSize(expected).allSatisfy(message -> {
            assertThat(message.getSubject()).contains(orderId.toString());
            assertThat(message.getContent().toString()).isNotBlank();
            assertThat(message.getFrom()[0].toString()).isEqualTo("loja@test.com");
        });
        assertThat(Arrays.stream(smtp.getReceivedMessages()).flatMap(message -> {
            try { return Arrays.stream(message.getRecipients(Message.RecipientType.TO)); }
            catch (Exception e) { throw new IllegalStateException(e); }
        }).map(Object::toString)).contains("cliente@test.com");
    }
}
