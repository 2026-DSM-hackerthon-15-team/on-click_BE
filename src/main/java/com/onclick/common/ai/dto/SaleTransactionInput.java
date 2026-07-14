package com.onclick.common.ai.dto;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public record SaleTransactionInput(
        LocalDateTime soldAt,
        long totalPaidAmount,
        Status status
) {

    public SaleTransactionInput {
        Objects.requireNonNull(soldAt, "soldAt must not be null");
        if (totalPaidAmount < 0) {
            throw new IllegalArgumentException("totalPaidAmount must be non-negative");
        }
        Objects.requireNonNull(status, "status must not be null");
        soldAt = soldAt.truncatedTo(ChronoUnit.SECONDS);
    }

    public enum Status {
        COMPLETED,
        CANCELLED
    }
}
