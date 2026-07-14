package com.onclick.domain.sale.dto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaleTransactionCreateRequest(
        @Size(max = 100) String clientTransactionId,
        @NotNull LocalDateTime soldAt,
        @NotEmpty @Size(max = 100) @Valid List<SaleItemRequest> items
) {
}
