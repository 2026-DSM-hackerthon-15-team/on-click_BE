package com.onclick.domain.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

public record HourlySalesResponse(
        Long storeId,
        LocalDate businessDate,
        String timeZone,
        String currency,
        long totalSalesAmount,
        long totalQuantity,
        long orderCount,
        List<HourlySalesItem> hourly
) {
    public HourlySalesResponse {
        hourly = List.copyOf(hourly);
    }
}
