package com.onclick.domain.product.dto;

import java.time.LocalDateTime;

import com.onclick.domain.product.entity.Product;

public record ProductResponse(
        Long id,
        Long storeId,
        String name,
        long price,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
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
