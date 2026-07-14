package com.onclick.common.ai.dto;

import java.time.LocalDate;
import java.util.List;

public record ConsultingGenerationRequest(
        Long consultingId,
        Long storeId,
        String storeName,
        LocalDate targetDate,
        long totalSalesAmount,
        long orderCount,
        long totalVisitors,
        long totalQuantity,
        List<ConsultingProductSales> products,
        List<ConsultingHourlySales> hourlySales
) {

    public ConsultingGenerationRequest {
        products = List.copyOf(products);
        hourlySales = List.copyOf(hourlySales);
    }
}
