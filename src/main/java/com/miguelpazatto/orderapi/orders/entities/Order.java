package com.miguelpazatto.orderapi.orders.entities;

import com.miguelpazatto.orderapi.core.exceptions.BusinessRuleException;
import com.miguelpazatto.orderapi.orders.entities.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tb_order", schema = "order_schema")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID customerId;

    @Column(nullable = false, updatable = false)
    private String customerName;

    @Column(nullable = false, updatable = false)
    private String customerEmail;

    @Column(nullable = false)
    private BigDecimal totalPrice;

    @Column(nullable = false, updatable = false)
    private Instant purchaseMoment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus orderStatus;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> orderItemList = new ArrayList<>();

    public record ItemData(UUID productId, String productName, Integer quantity, BigDecimal price) {}

    public Order(UUID customerId, String customerName, String customerEmail, List<ItemData> itemsData) {
        if (itemsData == null || itemsData.isEmpty()) {
            throw new BusinessRuleException("O pedido deve conter pelo menos um item.");
        }

        this.customerId = customerId;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.purchaseMoment = Instant.now();
        this.orderStatus = OrderStatus.WAITING_PAYMENT;

        for (ItemData data : itemsData) {
            OrderItem item = new OrderItem(
                    this,
                    data.productId(),
                    data.productName(),
                    data.quantity(),
                    data.price()
            );
            this.orderItemList.add(item);
        }

        this.totalPrice = calculateTotalPrice();
    }

    private BigDecimal calculateTotalPrice() {
        return this.orderItemList.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void cancel() {
        if (this.orderStatus == OrderStatus.SHIPPED ||
                this.orderStatus == OrderStatus.DELIVERED ||
                this.orderStatus == OrderStatus.CANCELED) {
            throw new BusinessRuleException("Pedido já enviado, entregue ou cancelado e não pode ser alterado.");
        }
        this.orderStatus = OrderStatus.CANCELED;
    }

    public void dispatch() {
        if (this.orderStatus != OrderStatus.PAID) {
            throw new BusinessRuleException("Apenas pedidos com status PAGO podem ser enviados.");
        }
        this.orderStatus = OrderStatus.SHIPPED;
    }

    public void deliver() {
        if (this.orderStatus != OrderStatus.SHIPPED) {
            throw new BusinessRuleException("O pedido precisa ser ENVIADO antes de ser marcado como entregue.");
        }
        this.orderStatus = OrderStatus.DELIVERED;
    }

    public void markAsPaid() {
        if (this.orderStatus == OrderStatus.CANCELED) {
            throw new BusinessRuleException("Não é possível aprovar o pagamento de um pedido cancelado.");
        }
        this.orderStatus = OrderStatus.PAID;
    }

    public void markAsPaymentFailed() {
        if (this.orderStatus == OrderStatus.WAITING_PAYMENT || this.orderStatus == OrderStatus.PAYMENT_FAILED) {
            this.orderStatus = OrderStatus.PAYMENT_FAILED;
        }
    }
}
