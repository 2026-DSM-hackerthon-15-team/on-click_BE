package com.onclick.domain.product.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductUpdateRequest(
        @Size(max = 100) String name,
        @PositiveOrZero Long price
) {
}
