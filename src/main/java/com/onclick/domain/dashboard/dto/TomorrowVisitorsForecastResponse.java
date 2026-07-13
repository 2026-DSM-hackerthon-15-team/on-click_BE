package com.onclick.domain.dashboard.dto;

import java.time.Instant;
import java.time.LocalDate;

public record TomorrowVisitorsForecastResponse(
        Long storeId,
        LocalDate targetDate,
        long expectedVisitors,
        Instant generatedAt,
        boolean mock
) {
}
