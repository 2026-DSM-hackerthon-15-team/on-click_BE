package com.onclick.common.ai.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;

public record TomorrowVisitorsForecastRequest(
        long storeId,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate baseDate,
        List<SaleTransactionInput> salesData
) {

    public TomorrowVisitorsForecastRequest {
        if (storeId <= 0) {
            throw new IllegalArgumentException("storeId must be positive");
        }
        Objects.requireNonNull(baseDate, "baseDate must not be null");
        Objects.requireNonNull(salesData, "salesData must not be null");
        if (salesData.isEmpty()) {
            throw new IllegalArgumentException("salesData must not be empty");
        }
        salesData = List.copyOf(salesData);
    }
}
