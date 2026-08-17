package com.miguelpazatto.orderapi.delivery.entities;

import com.miguelpazatto.orderapi.delivery.entities.enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tb_delivery")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID orderId;

    @Column
    private String trackingCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus deliveryStatus;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public Delivery(UUID orderId) {
        this.orderId = orderId;
        this.deliveryStatus = DeliveryStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void assignTrackingCode(String trackingCode) {
        this.trackingCode = trackingCode;
        this.updatedAt = Instant.now();
    }

    public void updateStatus(DeliveryStatus newStatus) {
        this.deliveryStatus = newStatus;
        this.updatedAt = Instant.now();
    }
}