package com.miguelpazatto.orderapi.customers.services;

import com.miguelpazatto.orderapi.auth.dtos.CustomerRegistrationDTO;
import com.miguelpazatto.orderapi.auth.entities.User;
import com.miguelpazatto.orderapi.core.exceptions.DataConflictException;
import com.miguelpazatto.orderapi.core.exceptions.ResourceNotFoundException;
import com.miguelpazatto.orderapi.customers.dtos.CustomerResponseDTO;
import com.miguelpazatto.orderapi.customers.dtos.CustomerUpdateDTO;
import com.miguelpazatto.orderapi.customers.entities.Customer;
import com.miguelpazatto.orderapi.customers.repositories.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public List<CustomerResponseDTO> findAll() {
        return customerRepository.findByActiveTrue().stream()
                .map(CustomerResponseDTO::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponseDTO findById(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente com ID " + id + " não encontrado"));

        return new CustomerResponseDTO(customer);
    }

    @Transactional
    public CustomerResponseDTO createCustomerProfile(CustomerRegistrationDTO dto, User user) {
        if (customerRepository.existsByEmail(dto.email())) {
            throw new DataConflictException("Este e-mail já está cadastrado no sistema.");
        }

        Customer customer = new Customer();
        customer.setName(dto.name());
        customer.setEmail(dto.email());
        customer.setPhone(dto.phone());

        customer.setUser(user);
        customer.setActive(true);

        customer = customerRepository.save(customer);
        return new CustomerResponseDTO(customer);
    }

    @Transactional
    public CustomerResponseDTO update(UUID id, CustomerUpdateDTO dto) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente com ID " + id + " não encontrado"));

        updateData(customer, dto);

        customer = customerRepository.save(customer);
        return new CustomerResponseDTO(customer);
    }

    @Transactional
    public void delete(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente com ID " + id + " não encontrado"));

        customer.setActive(false);

        User user = customer.getUser();
        if (user != null) {
            user.setActive(false);
        }

        customerRepository.save(customer);
    }

    public void updateData(Customer entity, CustomerUpdateDTO dto) {
        entity.setName(dto.name());
        entity.setPhone(dto.phone());
    }
}
