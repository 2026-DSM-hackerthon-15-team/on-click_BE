package com.onclick.domain.dashboard.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TomorrowVisitorsForecastResponse(
        Long storeId,
        LocalDate targetDate,
        long expectedVisitors,
        LocalDateTime generatedAt
) {
}
