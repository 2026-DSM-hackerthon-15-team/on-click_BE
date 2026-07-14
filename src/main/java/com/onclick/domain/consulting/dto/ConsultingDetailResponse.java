package com.onclick.domain.consulting.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.onclick.domain.consulting.entity.Consulting;
import com.onclick.domain.consulting.entity.ConsultingStatus;

public record ConsultingDetailResponse(
        Long consultingId,
        Long storeId,
        LocalDate targetDate,
        String title,
        String content,
        ConsultingStatus status,
        int attemptCount,
        LocalDateTime generatedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ConsultingDetailResponse from(Consulting consulting) {
        return new ConsultingDetailResponse(
                consulting.getId(),
                consulting.getStoreId(),
                consulting.getTargetDate(),
                consulting.getTitle(),
                consulting.getContent(),
                consulting.getStatus(),
                consulting.getAttemptCount(),
                consulting.getGeneratedAt(),
                consulting.getCreatedAt(),
                consulting.getUpdatedAt()
        );
    }
}
