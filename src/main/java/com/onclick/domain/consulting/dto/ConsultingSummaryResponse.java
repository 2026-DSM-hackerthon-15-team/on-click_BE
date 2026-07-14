package com.onclick.domain.consulting.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.onclick.domain.consulting.entity.Consulting;
import com.onclick.domain.consulting.entity.ConsultingStatus;

public record ConsultingSummaryResponse(
        Long consultingId,
        Long storeId,
        LocalDate targetDate,
        String title,
        ConsultingStatus status,
        int attemptCount,
        LocalDateTime generatedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
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
