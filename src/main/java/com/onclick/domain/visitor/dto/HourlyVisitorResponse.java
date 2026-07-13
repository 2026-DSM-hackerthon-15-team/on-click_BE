package com.onclick.domain.visitor.dto;

import com.onclick.domain.visitor.entity.HourlyVisitorCount;

import java.time.Instant;
import java.time.LocalDate;

public record HourlyVisitorResponse(
        Long id,
        Long storeId,
        LocalDate businessDate,
        int hour,
        long visitorCount,
        Instant createdAt,
        Instant updatedAt
) {

    public static HourlyVisitorResponse from(HourlyVisitorCount visitorCount) {
        return new HourlyVisitorResponse(
                visitorCount.getId(),
                visitorCount.getStoreId(),
                visitorCount.getBusinessDate(),
                visitorCount.getHour(),
                visitorCount.getVisitorCount(),
                visitorCount.getCreatedAt(),
                visitorCount.getUpdatedAt()
        );
    }
}
