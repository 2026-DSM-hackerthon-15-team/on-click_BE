package com.onclick.domain.dashboard.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ClosingSalesForecastResponse(
        Long storeId,
        LocalDate businessDate,
        String currency,
        long observedSalesAmount,
        long forecastClosingSalesAmount,
        LocalDateTime generatedAt
) {
}
