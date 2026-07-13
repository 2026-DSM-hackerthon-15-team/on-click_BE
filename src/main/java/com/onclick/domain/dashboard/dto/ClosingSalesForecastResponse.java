package com.onclick.domain.dashboard.dto;

import java.time.Instant;
import java.time.LocalDate;

public record ClosingSalesForecastResponse(
        Long storeId,
        LocalDate businessDate,
        String currency,
        long observedSalesAmount,
        long forecastClosingSalesAmount,
        Instant generatedAt
) {
}
