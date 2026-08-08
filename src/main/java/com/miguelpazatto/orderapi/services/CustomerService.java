package com.miguelpazatto.orderapi.services;

import com.miguelpazatto.orderapi.dtos.CustomerRequestDTO;
import com.miguelpazatto.orderapi.dtos.CustomerResponseDTO;
import com.miguelpazatto.orderapi.entities.Customer;
import com.miguelpazatto.orderapi.repositories.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    public List<CustomerResponseDTO> findAll() {
        return customerRepository.findByActiveTrue().stream()
                .map(CustomerResponseDTO::new)
                .toList();
    }

    public CustomerResponseDTO findById(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente com ID " + id + " não encontrado"));

        return new CustomerResponseDTO(customer);
    }

    public CustomerResponseDTO insert(CustomerRequestDTO dto) {
        if (customerRepository.existsByEmail(dto.email())) {
            throw new RuntimeException("Já existe um cliente cadastrado com email: " + dto.email());
        }

        Customer customer = new Customer();
        customer.setName(dto.name());
        customer.setEmail(dto.email());
        customer.setPhone(dto.phone());
        customer.setActive(true);

        customer = customerRepository.save(customer);
        return new CustomerResponseDTO(customer);
    }

    public CustomerResponseDTO update(UUID id, CustomerRequestDTO dto) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente com ID " + id + " não encontrado"));

        if (!customer.getEmail().equals(dto.email()) && customerRepository.existsByEmail(dto.email())) {
            throw new RuntimeException("Já existe um cliente cadastrado com email: " + dto.email());
        }

        updateData(customer, dto);

        customer = customerRepository.save(customer);
        return new CustomerResponseDTO(customer);
    }

    public void delete(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Cliente com ID " + id + " não encontrado"));

        customer.setActive(false);
        customerRepository.save(customer);
    }

    public void updateData(Customer entity, CustomerRequestDTO dto) {
        entity.setName(dto.name());
        entity.setEmail(dto.email());
        entity.setPhone(dto.phone());
    }
}
