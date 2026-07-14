package com.onclick.domain.dashboard.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DashboardSummaryResponse(
        Long storeId,
        LocalDate businessDate,
        String currency,
        long totalSalesAmount,
        long orderCount,
        long totalVisitors,
        LocalDateTime generatedAt
) {
}
