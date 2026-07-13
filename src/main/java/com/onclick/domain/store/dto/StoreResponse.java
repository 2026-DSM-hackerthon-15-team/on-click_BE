package com.onclick.domain.store.dto;

import java.time.Instant;

import com.onclick.domain.store.entity.Store;
import com.onclick.domain.store.entity.StoreRole;
import com.onclick.domain.store.entity.UserStoreMembership;

public record StoreResponse(
        Long id,
        String name,
        String timeZone,
        StoreRole role,
        Instant createdAt,
        Instant updatedAt
) {

    public static StoreResponse from(UserStoreMembership membership) {
        return from(membership.getStore(), membership.getRole());
    }

    public static StoreResponse from(Store store, StoreRole role) {
        return new StoreResponse(
                store.getId(),
                store.getName(),
                store.getTimeZone(),
                role,
                store.getCreatedAt(),
                store.getUpdatedAt()
        );
    }
}
