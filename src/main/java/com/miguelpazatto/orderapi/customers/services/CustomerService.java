package com.miguelpazatto.orderapi.customers.services;

import com.miguelpazatto.orderapi.auth.dtos.CustomerRegistrationDTO;
import com.miguelpazatto.orderapi.core.events.CustomerStatusChangedEvent;
import com.miguelpazatto.orderapi.core.events.UserRegisteredEvent;
import com.miguelpazatto.orderapi.core.exceptions.DataConflictException;
import com.miguelpazatto.orderapi.core.exceptions.ResourceNotFoundException;
import com.miguelpazatto.orderapi.customers.dtos.CustomerResponseDTO;
import com.miguelpazatto.orderapi.customers.dtos.CustomerUpdateDTO;
import com.miguelpazatto.orderapi.customers.entities.Customer;
import com.miguelpazatto.orderapi.customers.repositories.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final ApplicationEventPublisher eventPublisher;

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
    public CustomerResponseDTO update(UUID id, CustomerUpdateDTO dto) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente com ID " + id + " não encontrado"));

        updateData(customer, dto);

        customer = customerRepository.save(customer);
        return new CustomerResponseDTO(customer);
    }

    @Transactional
    public void deactivate(UUID id) {
        Customer customer = customerRepository.findById(id).orElseThrow();
        customer.deactivate();
        customerRepository.save(customer);

        eventPublisher.publishEvent(new CustomerStatusChangedEvent(customer.getUserId(), false));
    }

    @Transactional
    public void activate(UUID id) {
        Customer customer = customerRepository.findById(id).orElseThrow();
        customer.activate();
        customerRepository.save(customer);

        eventPublisher.publishEvent(new CustomerStatusChangedEvent(customer.getUserId(), true));
    }

    public void updateData(Customer entity, CustomerUpdateDTO dto) {
        entity.updateData(dto.name(), dto.phone());
    }

    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        Customer customer = new Customer(
                event.userId(),
                event.name(),
                event.email(),
                event.phone()
        );
        customerRepository.save(customer);
    }
}
