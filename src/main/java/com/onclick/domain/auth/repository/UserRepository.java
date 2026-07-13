package com.onclick.domain.auth.repository;

import java.util.Optional;

import com.onclick.domain.auth.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByAccountId(String accountId);

    boolean existsByAccountId(String accountId);
}
