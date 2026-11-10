package com.miguelpazatto.orderapi.unit.delivery.entities;

import com.miguelpazatto.orderapi.core.exceptions.BusinessRuleException;
import com.miguelpazatto.orderapi.delivery.entities.Delivery;
import com.miguelpazatto.orderapi.delivery.entities.enums.DeliveryStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class DeliveryTest {
    @Test
    void shouldAssignTrackingOnlyOnceAndIgnoreRepeatedStatus() {
        Delivery delivery = new Delivery(UUID.randomUUID());
        assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
        delivery.assignTrackingCode("TRACK-1");
        delivery.updateStatus(DeliveryStatus.IN_TRANSIT);
        var updated = delivery.getUpdatedAt();
        delivery.updateStatus(DeliveryStatus.IN_TRANSIT);
        assertThat(delivery.getUpdatedAt()).isEqualTo(updated);
        assertThatThrownBy(() -> delivery.assignTrackingCode("TRACK-2")).isInstanceOf(BusinessRuleException.class);
        assertThat(delivery.getTrackingCode()).isEqualTo("TRACK-1");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void shouldRejectInvalidTracking(String tracking) {
        assertThatThrownBy(() -> new Delivery(UUID.randomUUID()).assignTrackingCode(tracking))
                .isInstanceOf(BusinessRuleException.class);
    }

    @ParameterizedTest
    @EnumSource(value = DeliveryStatus.class, names = "DELIVERED", mode = EnumSource.Mode.EXCLUDE)
    void shouldRejectChangesAfterDelivery(DeliveryStatus status) {
        Delivery delivery = new Delivery(UUID.randomUUID());
        delivery.updateStatus(DeliveryStatus.DELIVERED);
        assertThatThrownBy(() -> delivery.updateStatus(status)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldRejectNullStatus() {
        assertThatThrownBy(() -> new Delivery(UUID.randomUUID()).updateStatus(null))
                .isInstanceOf(BusinessRuleException.class);
    }
}
