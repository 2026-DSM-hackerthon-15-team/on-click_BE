package com.onclick.common.ai.dto;

import java.time.LocalDate;
import java.util.Objects;

public record TomorrowVisitorsForecastRequest(long storeId, LocalDate targetDate) {

    public TomorrowVisitorsForecastRequest {
        if (storeId <= 0) {
            throw new IllegalArgumentException("storeId must be positive");
        }
        Objects.requireNonNull(targetDate, "targetDate must not be null");
    }
}
