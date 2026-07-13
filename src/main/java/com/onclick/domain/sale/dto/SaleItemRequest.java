package com.onclick.domain.sale.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record SaleItemRequest(
        @Positive int lineNo,
        @NotNull @Positive Long productId,
        @Positive int quantity,
        @PositiveOrZero long paidAmount
) {
}
