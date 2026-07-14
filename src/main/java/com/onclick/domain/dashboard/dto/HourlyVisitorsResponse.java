package com.onclick.domain.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

public record HourlyVisitorsResponse(
        Long storeId,
        LocalDate businessDate,
        long totalVisitors,
        List<HourlyVisitorItem> hourly
) {
    public HourlyVisitorsResponse {
        hourly = List.copyOf(hourly);
    }
}
