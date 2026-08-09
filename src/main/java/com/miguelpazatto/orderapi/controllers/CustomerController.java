package com.miguelpazatto.orderapi.controllers;

import com.miguelpazatto.orderapi.dtos.CustomerRequestDTO;
import com.miguelpazatto.orderapi.dtos.CustomerResponseDTO;
import com.miguelpazatto.orderapi.dtos.ProductResponseDTO;
import com.miguelpazatto.orderapi.services.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public ResponseEntity<List<CustomerResponseDTO>> findAll() {
        return ResponseEntity.ok().body(customerService.findAll());
    }

    @GetMapping(value = "/{id}")
    public ResponseEntity<CustomerResponseDTO> findById(@PathVariable UUID id) {
        return ResponseEntity.ok().body(customerService.findById(id));
    }

    @PostMapping
    public ResponseEntity<CustomerResponseDTO> insert(@RequestBody @Valid CustomerRequestDTO dto) {
        CustomerResponseDTO customer = customerService.insert(dto);
        URI uri = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(customer.id()).toUri();
        return ResponseEntity.created(uri).body(customer);
    }

    @PutMapping(value = "/{id}")
    public ResponseEntity<CustomerResponseDTO> update(@PathVariable UUID id, @RequestBody @Valid CustomerRequestDTO dto) {
        return ResponseEntity.ok().body(customerService.update(id, dto));
    }

    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
