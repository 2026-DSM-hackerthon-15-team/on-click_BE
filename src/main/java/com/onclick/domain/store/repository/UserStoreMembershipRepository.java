package com.onclick.domain.store.repository;

import java.util.List;
import java.util.Optional;

import com.onclick.domain.store.entity.UserStoreMembership;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserStoreMembershipRepository extends JpaRepository<UserStoreMembership, Long> {

    @EntityGraph(attributePaths = "store")
    List<UserStoreMembership> findAllByUserIdOrderByStoreIdAsc(Long userId);

    @EntityGraph(attributePaths = {"user", "store"})
    Optional<UserStoreMembership> findByUserIdAndStoreId(Long userId, Long storeId);

    boolean existsByUserIdAndStoreId(Long userId, Long storeId);
}
