package com.onclick.domain.store.dto;

import java.time.LocalDateTime;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.onclick.domain.store.entity.Industry;
import com.onclick.domain.store.entity.Store;

public record StoreResponse(
        Long id,
        String name,
        Industry industry,
        String roadAddress,
        @JsonFormat(pattern = "HH:mm") LocalTime closingTime,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static StoreResponse from(Store store) {
        return new StoreResponse(
                store.getId(),
                store.getName(),
                store.getIndustry(),
                store.getRoadAddress(),
                store.getClosingTime(),
                store.getCreatedAt(),
                store.getUpdatedAt()
        );
    }
}
