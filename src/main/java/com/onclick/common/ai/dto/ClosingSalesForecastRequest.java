package com.onclick.common.ai.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public record ClosingSalesForecastRequest(
        long storeId,
        LocalDateTime asOf,
        List<SaleTransactionInput> salesData
) {

    public ClosingSalesForecastRequest {
        if (storeId <= 0) {
            throw new IllegalArgumentException("storeId must be positive");
        }
        Objects.requireNonNull(asOf, "asOf must not be null");
        Objects.requireNonNull(salesData, "salesData must not be null");
        if (salesData.isEmpty()) {
            throw new IllegalArgumentException("salesData must not be empty");
        }
        salesData = List.copyOf(salesData);
    }
}
