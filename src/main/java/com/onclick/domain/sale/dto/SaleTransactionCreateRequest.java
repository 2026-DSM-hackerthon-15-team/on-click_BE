package com.onclick.domain.sale.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaleTransactionCreateRequest(
        @NotBlank @Size(max = 100) String transactionId,
        @NotNull Instant soldAt,
        @NotEmpty @Valid List<SaleItemRequest> items
) {
}
