package com.onclick.domain.instagram.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.onclick.domain.instagram.entity.InstagramIntegration;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InstagramIntegrationRepository extends JpaRepository<InstagramIntegration, Long> {

    Optional<InstagramIntegration> findByStoreId(Long storeId);

    List<InstagramIntegration> findAllByTokenExpiresAtBefore(Instant cutoff);
}
