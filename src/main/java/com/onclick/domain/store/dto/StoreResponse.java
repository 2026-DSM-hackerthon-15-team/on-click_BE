package com.onclick.domain.store.dto;

import java.time.Instant;

import com.onclick.domain.store.entity.Store;

public record StoreResponse(
        Long id,
        String name,
        String timeZone,
        Instant createdAt,
        Instant updatedAt
) {

    public static StoreResponse from(Store store) {
        return new StoreResponse(
                store.getId(),
                store.getName(),
                store.getTimeZone(),
                store.getCreatedAt(),
                store.getUpdatedAt()
        );
    }
}
