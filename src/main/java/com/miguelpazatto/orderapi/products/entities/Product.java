package com.miguelpazatto.orderapi.products.entities;

import com.miguelpazatto.orderapi.core.exceptions.BusinessRuleException;
import com.miguelpazatto.orderapi.core.exceptions.DataConflictException;
import com.miguelpazatto.orderapi.products.entities.enums.ProductStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "tb_product")
@Getter
@EqualsAndHashCode(of = "id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer availableStock;

    @Column(unique = true, nullable = false, updatable = false)
    private String sku;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus productStatus;

    public Product(String name, String description, BigDecimal price, Integer availableStock, String sku) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.availableStock = availableStock;
        this.sku = sku;

        this.productStatus = (availableStock != null && availableStock > 0)
                ? ProductStatus.ACTIVE
                : ProductStatus.OUT_OF_STOCK;
    }

    public void updateStock(Integer newStock) {
        if (newStock == null || newStock < 0) {
            throw new BusinessRuleException("O estoque não pode ser nulo ou negativo.");
        }
        this.availableStock = newStock;
        this.productStatus = (newStock > 0) ? ProductStatus.ACTIVE : ProductStatus.OUT_OF_STOCK;
    }

    public void decreaseStock(int quantity) {
        if (quantity <= 0) {
            throw new BusinessRuleException("A quantidade para baixar do estoque deve ser maior que zero.");
        }
        if (this.availableStock < quantity) {
            throw new BusinessRuleException("Estoque insuficiente para o produto: " + this.name);
        }
        this.availableStock -= quantity;

        if (this.availableStock == 0) {
            this.productStatus = ProductStatus.OUT_OF_STOCK;
        }
    }

    public void updatePrice(BigDecimal newPrice) {
        if (newPrice == null || newPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("O preço deve ser maior que zero.");
        }
        this.price = newPrice;
    }

    public void updateDetails(String newName, String newDescription) {
        if (newName != null && !newName.isBlank()) {
            this.name = newName;
        }
        if (newDescription != null && !newDescription.isBlank()) {
            this.description = newDescription;
        }
    }

    private void changeStatus(ProductStatus newStatus) {
        if (this.productStatus.equals(newStatus)) {
            throw new DataConflictException("O produto já se encontra com o status " + newStatus);
        }
        this.productStatus = newStatus;
    }

    public void deactivate() {
        changeStatus(ProductStatus.INACTIVE);
    }

    public void activate() {
        ProductStatus target = (this.availableStock > 0) ? ProductStatus.ACTIVE : ProductStatus.OUT_OF_STOCK;
        changeStatus(target);
    }
}