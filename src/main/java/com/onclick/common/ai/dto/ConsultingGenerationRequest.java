package com.onclick.common.ai.dto;

import java.time.LocalDate;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;

public record ConsultingGenerationRequest(
        Long userId,
        Long storeId,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate targetDate,
        String reportFormat
) {

    public static final String DAILY_V1 = "DAILY_V1";

    public ConsultingGenerationRequest {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (storeId == null || storeId <= 0) {
            throw new IllegalArgumentException("storeId must be positive");
        }
        Objects.requireNonNull(targetDate, "targetDate must not be null");
        reportFormat = reportFormat == null || reportFormat.isBlank()
                ? DAILY_V1
                : reportFormat.trim();
        if (!DAILY_V1.equals(reportFormat)) {
            throw new IllegalArgumentException("reportFormat must be DAILY_V1");
        }
    }
}
