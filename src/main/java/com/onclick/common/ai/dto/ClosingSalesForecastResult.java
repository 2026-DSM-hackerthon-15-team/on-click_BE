package com.onclick.common.ai.dto;

import java.time.Instant;
import java.util.Objects;

public record ClosingSalesForecastResult(
        long expectedClosingSales,
        Instant generatedAt,
        boolean mock
) {

    public ClosingSalesForecastResult {
        if (expectedClosingSales < 0) {
            throw new IllegalArgumentException("expectedClosingSales must be non-negative");
        }
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
    }
}
