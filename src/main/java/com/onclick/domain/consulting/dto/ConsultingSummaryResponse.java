package com.onclick.domain.consulting.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.onclick.domain.consulting.entity.Consulting;
import com.onclick.domain.consulting.entity.ConsultingStatus;

public record ConsultingSummaryResponse(
        Long consultingId,
        Long storeId,
        LocalDate targetDate,
        String title,
        ConsultingStatus status,
        int attemptCount,
        Instant generatedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static ConsultingSummaryResponse from(Consulting consulting) {
        return new ConsultingSummaryResponse(
                consulting.getId(),
                consulting.getStoreId(),
                consulting.getTargetDate(),
                consulting.getTitle(),
                consulting.getStatus(),
                consulting.getAttemptCount(),
                consulting.getGeneratedAt(),
                consulting.getCreatedAt(),
                consulting.getUpdatedAt()
        );
    }
}
