package com.onclick.domain.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @PositiveOrZero Long price
) {
}
