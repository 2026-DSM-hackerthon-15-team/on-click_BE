package com.onclick.common.ai.dto;

import java.time.LocalDate;
import java.util.Objects;

public record ClosingSalesForecastRequest(long storeId, LocalDate businessDate) {

    public ClosingSalesForecastRequest {
        if (storeId <= 0) {
            throw new IllegalArgumentException("storeId must be positive");
        }
        Objects.requireNonNull(businessDate, "businessDate must not be null");
    }
}
