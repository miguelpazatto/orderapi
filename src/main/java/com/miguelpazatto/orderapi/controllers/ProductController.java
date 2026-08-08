package com.miguelpazatto.orderapi.controllers;

import com.miguelpazatto.orderapi.dtos.ProductRequestDTO;
import com.miguelpazatto.orderapi.dtos.ProductResponseDTO;
import com.miguelpazatto.orderapi.dtos.ProductUpdateDetailsRequestDTO;
import com.miguelpazatto.orderapi.entities.enums.ProductStatus;
import com.miguelpazatto.orderapi.services.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<List<ProductResponseDTO>> findAll() {
        List<ProductResponseDTO> products = productService.findAll();
        return ResponseEntity.ok().body(products);
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<ProductResponseDTO> findById(
            @PathVariable UUID id) {
        ProductResponseDTO product = productService.findById(id);
        return ResponseEntity.ok().body(product);
    }

    @PostMapping
    public ResponseEntity<ProductResponseDTO> insert(
            @RequestBody @Valid ProductRequestDTO dto) {
        ProductResponseDTO product = productService.insert(dto);
        URI uri = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(product.id()).toUri();
        return ResponseEntity.created(uri).body(product);
    }

    @PatchMapping(value = "/{id}/stock")
    public ResponseEntity<ProductResponseDTO> updateStock(
            @PathVariable UUID id,
            @RequestParam Integer newStock) {
        ProductResponseDTO product = productService.updateStock(id, newStock);
        return ResponseEntity.ok().body(product);
    }

    @PatchMapping(value = "/{id}/price")
    public ResponseEntity<ProductResponseDTO> updatePrice(
            @PathVariable UUID id,
            @RequestParam BigDecimal newPrice) {
        ProductResponseDTO product = productService.updatePrice(id, newPrice);
        return ResponseEntity.ok().body(product);
    }

    @PatchMapping(value = "/{id}/status")
    public ResponseEntity<ProductResponseDTO> updateStatus
            (@PathVariable UUID id,
             @RequestParam ProductStatus productStatus) {
        ProductResponseDTO product = productService.updateStatus(id, productStatus);
        return ResponseEntity.ok().body(product);
    }

    @PatchMapping(value = "/{id}")
    public ResponseEntity<ProductResponseDTO> updateDetails(
            @PathVariable UUID id,
            @RequestBody @Valid ProductUpdateDetailsRequestDTO dto) {
        ProductResponseDTO product = productService.updateDetails(id, dto.name(), dto.description());
        return ResponseEntity.ok().body(product);
    }
}
