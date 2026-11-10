package com.miguelpazatto.orderapi.unit.customers.services;

import com.miguelpazatto.orderapi.core.events.CustomerStatusChangedEvent;
import com.miguelpazatto.orderapi.core.events.UserRegisteredEvent;
import com.miguelpazatto.orderapi.core.exceptions.ResourceNotFoundException;
import com.miguelpazatto.orderapi.customers.dtos.CustomerUpdateDTO;
import com.miguelpazatto.orderapi.customers.entities.Customer;
import com.miguelpazatto.orderapi.customers.repositories.CustomerRepository;
import com.miguelpazatto.orderapi.customers.services.CustomerService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {
    @Mock
    private CustomerRepository customers;
    @Mock
    private ApplicationEventPublisher events;
    @InjectMocks
    private CustomerService service;

    @Test
    void shouldListOnlyActiveProfilesAndHandleEmptyResults() {
        Customer customer = customer();
        when(customers.findByActiveTrue()).thenReturn(List.of(customer), List.of());
        assertThat(service.findAll()).singleElement().satisfies(dto -> assertThat(dto.email()).isEqualTo(customer.getEmail()));
        assertThat(service.findAll()).isEmpty();
    }

    @Test
    void shouldFindProfileByIdAndUserId() {
        UUID id = UUID.randomUUID();
        Customer customer = customer();
        when(customers.findById(id)).thenReturn(Optional.of(customer));
        when(customers.findByUserId(customer.getUserId())).thenReturn(Optional.of(customer));
        assertThat(service.findById(id).email()).isEqualTo(customer.getEmail());
        assertThat(service.findEntityById(id)).isSameAs(customer);
        assertThat(service.findByUserId(customer.getUserId())).isSameAs(customer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"find", "entity", "user", "update", "activate", "deactivate"})
    void shouldRejectMissingProfile(String operation) {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> {
            switch (operation) {
                case "find" -> service.findById(id);
                case "entity" -> service.findEntityById(id);
                case "user" -> service.findByUserId(id);
                case "update" -> service.update(id, new CustomerUpdateDTO("Novo", "(11) 99999-9999"));
                case "activate" -> service.activate(id);
                case "deactivate" -> service.deactivate(id);
                default -> throw new IllegalArgumentException(operation);
            }
        }).isInstanceOf(ResourceNotFoundException.class);
        verify(customers, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void shouldUpdateProfile() {
        UUID id = UUID.randomUUID();
        Customer customer = customer();
        when(customers.findById(id)).thenReturn(Optional.of(customer));
        when(customers.save(customer)).thenReturn(customer);
        assertThat(service.update(id, new CustomerUpdateDTO("Novo", "(11) 99999-9999")).name()).isEqualTo("Novo");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldChangeStatusAndPublishUserSynchronization(boolean active) {
        UUID id = UUID.randomUUID();
        Customer customer = customer();
        when(customers.findById(id)).thenReturn(Optional.of(customer));
        if (active) service.activate(id); else service.deactivate(id);
        assertThat(customer.isActive()).isEqualTo(active);
        verify(events).publishEvent(new CustomerStatusChangedEvent(customer.getUserId(), active));
        verify(customers).save(customer);
    }

    @Test
    void shouldCreateProfileFromRegistrationEvent() {
        UUID userId = UUID.randomUUID();
        service.onUserRegistered(new UserRegisteredEvent(userId, "Cliente", "user@test.com", "phone"));
        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customers).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(userId);
        assertThat(saved.getValue().getEmail()).isEqualTo("user@test.com");
    }

    private Customer customer() {
        return new Customer(UUID.randomUUID(), "Cliente", "user@test.com", "phone");
    }
}
