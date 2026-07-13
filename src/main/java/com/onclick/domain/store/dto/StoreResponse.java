package com.onclick.domain.store.dto;

import java.time.Instant;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.onclick.domain.store.entity.Store;

public record StoreResponse(
        Long id,
        String name,
        String timeZone,
        @JsonFormat(pattern = "HH:mm") LocalTime closingTime,
        Instant createdAt,
        Instant updatedAt
) {

    public static StoreResponse from(Store store) {
        return new StoreResponse(
                store.getId(),
                store.getName(),
                store.getTimeZone(),
                store.getClosingTime(),
                store.getCreatedAt(),
                store.getUpdatedAt()
        );
    }
}
