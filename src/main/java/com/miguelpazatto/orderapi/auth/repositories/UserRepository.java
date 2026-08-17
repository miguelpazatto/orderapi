package com.miguelpazatto.orderapi.auth.repositories;

import com.miguelpazatto.orderapi.auth.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    public UserDetails findByEmail(String email);

}
