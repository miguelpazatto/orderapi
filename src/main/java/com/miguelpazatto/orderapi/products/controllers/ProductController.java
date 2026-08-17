package com.miguelpazatto.orderapi.products.controllers;

import com.miguelpazatto.orderapi.products.dtos.ProductRequestDTO;
import com.miguelpazatto.orderapi.products.dtos.ProductResponseDTO;
import com.miguelpazatto.orderapi.products.dtos.ProductUpdateDetailsRequestDTO;
import com.miguelpazatto.orderapi.products.entities.enums.ProductStatus;
import com.miguelpazatto.orderapi.products.services.ProductService;
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
        return ResponseEntity.ok().body(productService.findAll());
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<ProductResponseDTO> findById(@PathVariable UUID id) {
        return ResponseEntity.ok().body(productService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ProductResponseDTO> insert(@RequestBody @Valid ProductRequestDTO dto) {
        ProductResponseDTO product = productService.insert(dto);
        URI uri = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(product.id()).toUri();
        return ResponseEntity.created(uri).body(product);
    }

    @PatchMapping(value = "/{id}/stock")
    public ResponseEntity<ProductResponseDTO> updateStock(@PathVariable UUID id, @RequestParam Integer newStock) {
        return ResponseEntity.ok().body(productService.updateStock(id, newStock));
    }

    @PatchMapping(value = "/{id}/price")
    public ResponseEntity<ProductResponseDTO> updatePrice(@PathVariable UUID id, @RequestParam BigDecimal newPrice) {
        return ResponseEntity.ok().body(productService.updatePrice(id, newPrice));
    }

    @PatchMapping(value = "/{id}/status")
    public ResponseEntity<ProductResponseDTO> updateStatus(@PathVariable UUID id, @RequestParam ProductStatus productStatus) {
        return ResponseEntity.ok().body(productService.updateStatus(id, productStatus));
    }

    @PatchMapping(value = "/{id}")
    public ResponseEntity<ProductResponseDTO> updateDetails(@PathVariable UUID id, @RequestBody @Valid ProductUpdateDetailsRequestDTO dto) {
        return ResponseEntity.ok().body(productService.updateDetails(id, dto.name(), dto.description()));
    }
}
