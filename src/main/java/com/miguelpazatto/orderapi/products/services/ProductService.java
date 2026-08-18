package com.miguelpazatto.orderapi.products.services;

import com.miguelpazatto.orderapi.core.exceptions.BusinessRuleException;
import com.miguelpazatto.orderapi.core.exceptions.DataConflictException;
import com.miguelpazatto.orderapi.core.exceptions.ResourceNotFoundException;
import com.miguelpazatto.orderapi.products.dtos.ProductRequestDTO;
import com.miguelpazatto.orderapi.products.dtos.ProductResponseDTO;
import com.miguelpazatto.orderapi.products.entities.Product;
import com.miguelpazatto.orderapi.products.repositories.ProductRepository;
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

    public Product save(Product product) {
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> findAll() {
        return productRepository.findAll().stream()
                .map(ProductResponseDTO::new)
                .toList();
    }

    public Product findEntityById(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto com ID " + id + " não encontrado"));
    }

    @Transactional(readOnly = true)
    public ProductResponseDTO findById(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto com ID " + id + " não encontrado"));

        return new ProductResponseDTO(product);
    }

    @Transactional
    public ProductResponseDTO insert(ProductRequestDTO dto) {
        if (productRepository.existsBySku(dto.sku())) {
            throw new DataConflictException("Já existe um produto cadastrado com o SKU: " + dto.sku());
        }

        Product product = new Product(
                dto.name(),
                dto.description(),
                dto.price(),
                dto.availableStock(),
                dto.sku()
        );

        product = productRepository.save(product);
        return new ProductResponseDTO(product);
    }


    @Transactional
    public ProductResponseDTO updateStock(UUID id, Integer newStock) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto com ID " + id + " não encontrado"));

        product.updateStock(newStock);

        product = productRepository.save(product);
        return new ProductResponseDTO(product);
    }

    @Transactional
    public ProductResponseDTO updatePrice(UUID id, BigDecimal newPrice) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto com ID " + id + " não encontrado"));

        if (newPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("O valor do produto deve ser maior que zero");
        }

        product.updatePrice(newPrice);

        product = productRepository.save(product);
        return new ProductResponseDTO(product);
    }

    @Transactional
    public void deactivate(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto com ID " + id + " não encontrado"));
        product.deactivate();
        productRepository.save(product);
    }

    @Transactional
    public void activate(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto com ID " + id + " não encontrado"));
        product.activate();
        productRepository.save(product);
    }

    @Transactional
    public ProductResponseDTO updateDetails(UUID id, String newName, String newDescription) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto com ID " + id + " não encontrado"));

        product.updateDetails(newName, newDescription);

        product = productRepository.save(product);
        return new ProductResponseDTO(product);
    }
}
