package com.miguelpazatto.orderapi.services;

import com.miguelpazatto.orderapi.dtos.ProductRequestDTO;
import com.miguelpazatto.orderapi.dtos.ProductResponseDTO;
import com.miguelpazatto.orderapi.entities.Product;
import com.miguelpazatto.orderapi.entities.enums.ProductStatus;
import com.miguelpazatto.orderapi.repositories.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> findAll() {
        return productRepository.findAll().stream()
                .map(ProductResponseDTO::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponseDTO findById(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto com ID " + id + " não encontrado"));

        return new ProductResponseDTO(product);
    }

    public ProductResponseDTO insert(ProductRequestDTO dto) {
        if (productRepository.existsBySku(dto.sku())) {
            throw new RuntimeException("Já existe um produto cadastrado com o SKU: " + dto.sku());
        }

        Product product = new Product();
        product.setName(dto.name());
        product.setDescription(dto.description());
        product.setPrice(dto.price());
        product.setAvailableStock(dto.availableStock());
        product.setSku(dto.sku());
        product.setProductStatus(dto.availableStock() > 0 ? ProductStatus.ACTIVE : ProductStatus.OUT_OF_STOCK);

        product = productRepository.save(product);
        return new ProductResponseDTO(product);
    }


    public ProductResponseDTO updateStock(UUID id, Integer newStock) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto com ID " + id + " não encontrado"));

        product.setAvailableStock(newStock);
        product.setProductStatus(newStock > 0 ? ProductStatus.ACTIVE : ProductStatus.OUT_OF_STOCK);

        product = productRepository.save(product);
        return new ProductResponseDTO(product);
    }

    public ProductResponseDTO updatePrice(UUID id, BigDecimal newPrice) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto com ID " + id + " não encontrado"));

        if (newPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("O valor do produto deve ser maior que zero");
        }

        product.setPrice(newPrice);

        product = productRepository.save(product);
        return new ProductResponseDTO(product);
    }

    public ProductResponseDTO updateStatus(UUID id, ProductStatus productStatus) {
        if (productStatus == null) {
            throw new RuntimeException("O status do produto não pode ser nulo");
        }

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto com ID " + id + " não encontrado"));

        if (product.getProductStatus().equals(productStatus)) {
            throw new RuntimeException("O produto já se encontra com o status " + productStatus);
        }

        product.setProductStatus(productStatus);

        product = productRepository.save(product);
        return new ProductResponseDTO(product);
    }

    public ProductResponseDTO updateDetails(UUID id, String newName, String newDescription) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto com ID " + id + " não encontrado"));

        if (newName != null && !newName.isBlank()) {
            product.setName(newName);
        }

        if (newDescription != null && !newDescription.isBlank()) {
            product.setDescription(newDescription);
        }

        product = productRepository.save(product);
        return new ProductResponseDTO(product);
    }
}
