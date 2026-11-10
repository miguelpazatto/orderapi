package com.miguelpazatto.orderapi.integration.infra;

import com.miguelpazatto.orderapi.auth.entities.User;
import com.miguelpazatto.orderapi.auth.entities.enums.UserRole;
import com.miguelpazatto.orderapi.auth.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TestUserFactory {

    private final UserRepository userRepository;

    public User createCustomer() {
        return userRepository.save(new User(
                "customer-" + UUID.randomUUID() + "@test.com",
                "1234",
                Set.of(UserRole.CUSTOMER)
        ));
    }

    public User createAdmin() {
        User admin = new User(
                "admin-" + UUID.randomUUID() + "@test.com",
                "1234",
                Set.of(UserRole.ADMIN)
        );

        return userRepository.save(admin);
    }
}
