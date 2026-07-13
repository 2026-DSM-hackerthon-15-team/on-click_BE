package com.onclick.common.ai.dto;

public record ConsultingHourlySales(
        int hour,
        long salesAmount,
        long orderCount
) {
}
