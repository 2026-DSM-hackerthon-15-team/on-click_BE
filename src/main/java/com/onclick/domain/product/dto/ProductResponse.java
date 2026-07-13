package com.onclick.domain.product.dto;

import java.time.Instant;

import com.onclick.domain.product.entity.Product;

public record ProductResponse(
        Long id,
        Long storeId,
        String name,
        long price,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getStoreId(),
                product.getName(),
                product.getPrice(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
