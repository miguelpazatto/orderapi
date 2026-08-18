package com.miguelpazatto.orderapi.orders.entities;

import com.miguelpazatto.orderapi.core.exceptions.BusinessRuleException;
import com.miguelpazatto.orderapi.products.entities.Product;
import jakarta.persistence.*;
import lombok.*;

import javax.accessibility.Accessible;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "tb_order_item", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"order_id", "product_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(nullable = false, updatable = false)
    private String productName;

    @Column(nullable = false, updatable = false)
    private Integer quantity;

    @Column(nullable = false, updatable = false)
    private BigDecimal price;

    public OrderItem(Order order, UUID productId, String productName, Integer quantity, BigDecimal price) {
        this.order = order;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.price = price;
    }

}
