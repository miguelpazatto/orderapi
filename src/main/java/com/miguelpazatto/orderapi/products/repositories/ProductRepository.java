package com.miguelpazatto.orderapi.products.repositories;

import com.miguelpazatto.orderapi.products.entities.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    boolean existsBySku(String sku);
}
