package com.onclick.common.ai.dto;

import java.time.LocalDate;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;

public record ClosingSalesForecastRequest(
        long storeId,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate businessDate
) {

    public ClosingSalesForecastRequest {
        if (storeId <= 0) {
            throw new IllegalArgumentException("storeId must be positive");
        }
        Objects.requireNonNull(businessDate, "businessDate must not be null");
    }
}
