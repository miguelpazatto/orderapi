package com.miguelpazatto.orderapi.customers.controllers;

import com.miguelpazatto.orderapi.customers.dtos.CustomerResponseDTO;
import com.miguelpazatto.orderapi.customers.dtos.CustomerUpdateDTO;
import com.miguelpazatto.orderapi.customers.services.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @PatchMapping(value = "/{id}")
    public ResponseEntity<CustomerResponseDTO> update(@PathVariable UUID id, @RequestBody @Valid CustomerUpdateDTO dto) {
        return ResponseEntity.ok().body(customerService.update(id, dto));
    }

    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        customerService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping(value = "/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable UUID id) {
        customerService.activate(id);
        return ResponseEntity.noContent().build();
    }
}
