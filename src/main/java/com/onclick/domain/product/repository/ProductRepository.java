package com.onclick.domain.product.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.onclick.domain.product.entity.Product;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByStoreIdOrderByCreatedAtDesc(Long storeId);

    Optional<Product> findByIdAndStoreId(Long id, Long storeId);

    List<Product> findAllByStoreIdAndIdIn(Long storeId, Collection<Long> ids);
}
