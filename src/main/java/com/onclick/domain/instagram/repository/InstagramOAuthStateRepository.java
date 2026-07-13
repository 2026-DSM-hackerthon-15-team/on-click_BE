package com.onclick.domain.instagram.repository;

import java.time.Instant;
import java.util.Optional;

import com.onclick.domain.instagram.entity.InstagramOAuthState;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface InstagramOAuthStateRepository extends JpaRepository<InstagramOAuthState, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InstagramOAuthState> findByStateHash(String stateHash);

    long deleteByExpiresAtBefore(Instant cutoff);
}
