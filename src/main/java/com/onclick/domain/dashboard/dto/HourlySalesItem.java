package com.onclick.domain.dashboard.dto;

public record HourlySalesItem(
        int hour,
        long salesAmount,
        long quantity,
        long orderCount
) {
}
