package com.onclick.common.ai.dto;

import java.time.Instant;
import java.util.Objects;

public record TomorrowVisitorsForecastResult(
        long expectedVisitors,
        Instant generatedAt,
        boolean mock
) {

    public TomorrowVisitorsForecastResult {
        if (expectedVisitors < 0) {
            throw new IllegalArgumentException("expectedVisitors must be non-negative");
        }
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
    }
}
