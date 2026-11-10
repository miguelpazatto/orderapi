package com.miguelpazatto.orderapi.unit.orders.entities;

import com.miguelpazatto.orderapi.core.exceptions.BusinessRuleException;
import com.miguelpazatto.orderapi.orders.entities.Order;
import com.miguelpazatto.orderapi.orders.entities.enums.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    void shouldCreateOrderWithItemsAndExactTotal() {
        UUID customerId = UUID.randomUUID();
        Instant before = Instant.now();
        Order order = new Order(customerId, "Cliente", "cliente@test.com", List.of(
                item(3, "0.10"), item(2, "19.95")
        ));

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.WAITING_PAYMENT);
        assertThat(order.getPurchaseMoment()).isBetween(before, Instant.now());
        assertThat(order.getTotalPrice()).isEqualByComparingTo("40.20");
        assertThat(order.getOrderItemList()).hasSize(2).allSatisfy(item ->
                assertThat(item.getOrder()).isSameAs(order));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldRejectOrderWithoutItems(List<Order.ItemData> items) {
        assertThatThrownBy(() -> new Order(UUID.randomUUID(), "Cliente", "cliente@test.com", items))
                .isInstanceOf(BusinessRuleException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1, 1000})
    void shouldRejectInvalidItemQuantity(Integer quantity) {
        assertThatThrownBy(() -> new Order(UUID.randomUUID(), "Cliente", "cliente@test.com",
                List.of(item(quantity, "10.00"))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-0.01"})
    void shouldRejectInvalidItemPrice(String price) {
        assertThatThrownBy(() -> new Order(UUID.randomUUID(), "Cliente", "cliente@test.com",
                List.of(item(1, price))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldAcceptMaximumItemQuantity() {
        Order order = new Order(UUID.randomUUID(), "Cliente", "cliente@test.com", List.of(item(999, "1.00")));
        assertThat(order.getTotalPrice()).isEqualByComparingTo("999.00");
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"WAITING_PAYMENT", "PAYMENT_FAILED", "PAID"})
    void shouldCancelEligibleOrder(OrderStatus status) {
        Order order = orderWithStatus(status);
        order.cancel();
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"SHIPPED", "DELIVERED", "CANCELED"})
    void shouldRejectCancellationInInvalidStatus(OrderStatus status) {
        Order order = orderWithStatus(status);
        assertThatThrownBy(order::cancel).isInstanceOf(BusinessRuleException.class);
        assertThat(order.getOrderStatus()).isEqualTo(status);
    }

    @Test
    void shouldDispatchPaidOrder() {
        Order order = orderWithStatus(OrderStatus.PAID);
        order.dispatch();
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PAID", mode = EnumSource.Mode.EXCLUDE)
    void shouldRejectDispatchInInvalidStatus(OrderStatus status) {
        Order order = orderWithStatus(status);
        assertThatThrownBy(order::dispatch).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldDeliverShippedOrder() {
        Order order = orderWithStatus(OrderStatus.SHIPPED);
        order.deliver();
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "SHIPPED", mode = EnumSource.Mode.EXCLUDE)
    void shouldRejectDeliveryInInvalidStatus(OrderStatus status) {
        Order order = orderWithStatus(status);
        assertThatThrownBy(order::deliver).isInstanceOf(BusinessRuleException.class);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"WAITING_PAYMENT", "PAYMENT_FAILED"})
    void shouldApprovePendingPayment(OrderStatus status) {
        Order order = orderWithStatus(status);
        order.markAsPaid();
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PAID);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PAID", "SHIPPED", "DELIVERED"})
    void shouldIgnoreRepeatedPaymentApproval(OrderStatus status) {
        Order order = orderWithStatus(status);
        order.markAsPaid();
        assertThat(order.getOrderStatus()).isEqualTo(status);
    }

    @Test
    void shouldRejectPaymentApprovalForCanceledOrder() {
        Order order = orderWithStatus(OrderStatus.CANCELED);
        assertThatThrownBy(order::markAsPaid).isInstanceOf(BusinessRuleException.class);
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void shouldOnlyFailPendingPayments(OrderStatus status) {
        Order order = orderWithStatus(status);
        order.markAsPaymentFailed();
        OrderStatus expected = status == OrderStatus.WAITING_PAYMENT ? OrderStatus.PAYMENT_FAILED : status;
        assertThat(order.getOrderStatus()).isEqualTo(expected);
    }

    @Test
    void shouldIdentifyOrderOwner() {
        Order order = orderWithStatus(OrderStatus.WAITING_PAYMENT);
        assertThat(order.isOwnedBy(order.getCustomerId())).isTrue();
        assertThat(order.isOwnedBy(UUID.randomUUID())).isFalse();
    }

    private Order.ItemData item(Integer quantity, String price) {
        return new Order.ItemData(UUID.randomUUID(), "Produto", quantity,
                price == null ? null : new BigDecimal(price));
    }

    private Order orderWithStatus(OrderStatus status) {
        Order order = new Order(UUID.randomUUID(), "Cliente", "cliente@test.com", List.of(item(2, "10.00")));
        switch (status) {
            case WAITING_PAYMENT -> { }
            case PAYMENT_FAILED -> order.markAsPaymentFailed();
            case PAID -> order.markAsPaid();
            case SHIPPED -> { order.markAsPaid(); order.dispatch(); }
            case DELIVERED -> { order.markAsPaid(); order.dispatch(); order.deliver(); }
            case CANCELED -> order.cancel();
        }
        return order;
    }
}
