package com.miguelpazatto.orderapi.unit.customers.entities;

import com.miguelpazatto.orderapi.customers.entities.Customer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class CustomerTest {
    @Test
    void shouldActivateDeactivateAndUpdateProfile() {
        Customer customer = new Customer(UUID.randomUUID(), "Original", "user@test.com", "phone");
        assertThat(customer.isActive()).isTrue();
        customer.deactivate();
        assertThat(customer.isActive()).isFalse();
        customer.activate();
        customer.updateData("Novo", "new-phone");
        assertThat(customer.isActive()).isTrue();
        assertThat(customer.getName()).isEqualTo("Novo");
        assertThat(customer.getPhone()).isEqualTo("new-phone");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void shouldPreserveFieldsWhenUpdateIsBlank(String value) {
        Customer customer = new Customer(UUID.randomUUID(), "Original", "user@test.com", "phone");
        customer.updateData(value, value);
        assertThat(customer.getName()).isEqualTo("Original");
        assertThat(customer.getPhone()).isEqualTo("phone");
    }
}
