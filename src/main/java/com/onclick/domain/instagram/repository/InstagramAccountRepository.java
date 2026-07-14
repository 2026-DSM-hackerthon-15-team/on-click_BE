package com.onclick.domain.instagram.repository;

import java.util.Optional;

import com.onclick.domain.instagram.entity.InstagramAccount;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InstagramAccountRepository extends JpaRepository<InstagramAccount, Long> {

    Optional<InstagramAccount> findByStore_Id(Long storeId);
}
