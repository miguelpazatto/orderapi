package com.miguelpazatto.orderapi.unit.payments.entities;

import com.miguelpazatto.orderapi.payments.entities.Payment;
import com.miguelpazatto.orderapi.payments.entities.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.*;

class PaymentTest {
    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void shouldStartPendingAndTimestampStatusChange(PaymentStatus status) {
        Payment payment = new Payment(UUID.randomUUID(), new BigDecimal("10.25"));
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        Instant created = payment.getCreatedAt();
        payment.updateStatus(status);
        assertThat(payment.getPaymentStatus()).isEqualTo(status);
        assertThat(payment.getCreatedAt()).isEqualTo(created);
        assertThat(payment.getUpdatedAt()).isAfterOrEqualTo(created);
    }
}
