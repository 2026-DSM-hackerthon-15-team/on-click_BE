package com.onclick.domain.store.repository;

import java.util.List;
import java.util.Optional;

import com.onclick.domain.store.entity.Store;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {

    @EntityGraph(attributePaths = "owner")
    List<Store> findAllByOwnerIdOrderByIdAsc(Long ownerUserId);

    @EntityGraph(attributePaths = "owner")
    Optional<Store> findByIdAndOwnerId(Long storeId, Long ownerUserId);
}
