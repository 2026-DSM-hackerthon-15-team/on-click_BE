package com.onclick.common.ai.dto;

import java.time.LocalDate;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;

public record TomorrowVisitorsForecastRequest(
        long storeId,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate targetDate
) {

    public TomorrowVisitorsForecastRequest {
        if (storeId <= 0) {
            throw new IllegalArgumentException("storeId must be positive");
        }
        Objects.requireNonNull(targetDate, "targetDate must not be null");
    }
}
