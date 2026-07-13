package com.onclick.common.ai.dto;

public record ConsultingProductSales(
        Long productId,
        String productName,
        long quantity,
        long salesAmount
) {
}
