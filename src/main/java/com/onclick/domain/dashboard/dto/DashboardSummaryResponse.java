package com.onclick.domain.dashboard.dto;

import java.time.Instant;
import java.time.LocalDate;

public record DashboardSummaryResponse(
        Long storeId,
        LocalDate businessDate,
        String timeZone,
        String currency,
        long totalSalesAmount,
        long orderCount,
        long totalVisitors,
        Instant generatedAt
) {
}
