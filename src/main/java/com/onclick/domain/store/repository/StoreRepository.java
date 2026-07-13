package com.onclick.domain.store.repository;

import com.onclick.domain.store.entity.Store;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, Long> {
}
