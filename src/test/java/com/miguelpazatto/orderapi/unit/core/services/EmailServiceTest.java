package com.miguelpazatto.orderapi.unit.core.services;

import com.miguelpazatto.orderapi.core.services.EmailService;
import com.miguelpazatto.orderapi.payments.entities.enums.PaymentStatus;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private JavaMailSender mailSender;
    private EmailService emailService;
    private final UUID orderId = UUID.randomUUID();
    private final String recipient = "cliente@test.com";

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "remetente", "loja@test.com");
        ReflectionTestUtils.setField(emailService, "emailAdmin", "admin@test.com");
    }

    @Test
    void shouldComposeShippingEmailForCustomer() {
        emailService.enviarEmailPedidoEnviado(orderId, recipient);
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("loja@test.com");
        assertThat(message.getValue().getTo()).containsExactly(recipient);
        assertThat(message.getValue().getSubject()).contains(orderId.toString(), "enviado");
        assertThat(message.getValue().getText()).contains("enviá-lo");
    }

    @Test
    void shouldComposeDeliveryEmailsForAdminAndCustomer() {
        emailService.enviarEmailConfirmacaoEntrega(orderId, recipient);
        ArgumentCaptor<SimpleMailMessage> messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(2)).send(messages.capture());
        assertThat(messages.getAllValues().get(0).getTo()).containsExactly("admin@test.com");
        assertThat(messages.getAllValues().get(1).getTo()).containsExactly(recipient);
        assertThat(messages.getAllValues()).allSatisfy(message ->
                assertThat(message.getSubject()).contains(orderId.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"expiration", "warning"})
    void shouldComposeExpirationEmail(String operation) {
        send(operation);
        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly(recipient);
        assertThat(message.getValue().getSubject()).contains(orderId.toString(),
                operation.equals("expiration") ? "cancelado" : "expirar");
    }

    @ParameterizedTest
    @ValueSource(strings = {"shipping", "delivery", "expiration", "warning"})
    void shouldHandleSmtpFailureWithoutPropagatingIt(String operation) {
        doThrow(new MailSendException("SMTP indisponível")).when(mailSender).send(any(SimpleMailMessage.class));
        assertThatCode(() -> send(operation)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"APPROVED", "REJECTED"})
    void shouldHandlePaymentEmailFailure(PaymentStatus status) {
        doThrow(new MailSendException("SMTP indisponível")).when(mailSender).send(any(SimpleMailMessage.class));
        assertThatCode(() -> emailService.enviarEmailAtualizacaoPagamento(orderId, status, recipient))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldNotEmailPendingPayment() {
        emailService.enviarEmailAtualizacaoPagamento(orderId, PaymentStatus.PENDING, recipient);
        verifyNoInteractions(mailSender);
    }

    private void send(String operation) {
        switch (operation) {
            case "shipping" -> emailService.enviarEmailPedidoEnviado(orderId, recipient);
            case "delivery" -> emailService.enviarEmailConfirmacaoEntrega(orderId, recipient);
            case "expiration" -> emailService.enviarEmailCancelamentoPorInatividade(orderId, recipient);
            case "warning" -> emailService.enviarEmailAvisoExpiracao(orderId, recipient);
            default -> throw new IllegalArgumentException(operation);
        }
    }
}
