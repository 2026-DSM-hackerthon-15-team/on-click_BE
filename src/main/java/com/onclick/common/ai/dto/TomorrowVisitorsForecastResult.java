package com.onclick.common.ai.dto;

import java.time.LocalDateTime;
import java.util.Objects;

public record TomorrowVisitorsForecastResult(
        long expectedVisitors,
        LocalDateTime generatedAt
) {

    public TomorrowVisitorsForecastResult {
        if (expectedVisitors < 0) {
            throw new IllegalArgumentException("expectedVisitors must be non-negative");
        }
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
    }
}
